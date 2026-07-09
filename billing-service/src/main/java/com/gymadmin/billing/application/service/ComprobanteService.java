package com.gymadmin.billing.application.service;

import com.gymadmin.billing.application.command.EmitirFacturaCommand;
import com.gymadmin.billing.domain.model.ClaveAcceso;
import com.gymadmin.billing.domain.model.Comprobante;
import com.gymadmin.billing.domain.port.in.ComprobanteUseCase;
import com.gymadmin.billing.domain.port.out.CertificadoRepository;
import com.gymadmin.billing.domain.port.out.ComprobanteRepository;
import com.gymadmin.billing.domain.port.out.ConfigSriRepository;
import com.gymadmin.billing.domain.port.out.XmlSignaturePort;
import com.gymadmin.billing.infrastructure.adapter.out.xml.FacturaXmlBuilder;
import com.gymadmin.billing.infrastructure.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ComprobanteService implements ComprobanteUseCase {

    private final ComprobanteRepository comprobanteRepository;
    private final ConfigSriRepository configSriRepository;
    private final CertificadoRepository certificadoRepository;
    private final XmlSignaturePort xmlSignaturePort;
    private final FacturaXmlBuilder facturaXmlBuilder;
    private final EnvioSriService envioSriService;

    @Override
    public Mono<Comprobante> emitirFactura(EmitirFacturaCommand command) {
        return configSriRepository.findByEmpresa(command.idCompania(), command.idSucursal())
                .switchIfEmpty(Mono.error(new NotFoundException("Configuración SRI no encontrada para la empresa")))
                .flatMap(config -> {
                    ClaveAcceso claveAcceso = ClaveAcceso.generar(
                            command.fechaEmision(),
                            "01",
                            config.getRuc(),
                            config.getAmbiente(),
                            config.getCodEstablecimiento(),
                            config.getCodPuntoEmision(),
                            command.secuencial(),
                            command.codigoNumerico()
                    );

                    BigDecimal subtotalSinImpuesto = command.detalles().stream()
                            .map(EmitirFacturaCommand.DetalleFacturaItem::precioTotalSinImpuesto)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal total = command.pagos().stream()
                            .map(EmitirFacturaCommand.PagoItem::total)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    Comprobante comprobante = Comprobante.builder()
                            .idCompania(command.idCompania())
                            .idSucursal(command.idSucursal())
                            .tipoComprobante("01")
                            .claveAcceso(claveAcceso.getValue())
                            .codEstablecimiento(config.getCodEstablecimiento())
                            .codPuntoEmision(config.getCodPuntoEmision())
                            .secuencial(command.secuencial())
                            .fechaEmision(command.fechaEmision())
                            .ambiente(config.getAmbiente())
                            .tipoIdReceptor(command.tipoIdReceptor())
                            .idReceptor(command.idReceptor())
                            .razonSocialReceptor(command.razonSocialReceptor())
                            .emailReceptor(command.emailReceptor())
                            .direccionReceptor(command.direccionReceptor())
                            .telefonoReceptor(command.telefonoReceptor())
                            .subtotalSinImpuesto(subtotalSinImpuesto)
                            .subtotalIva0(BigDecimal.ZERO)
                            .subtotalNoObjetoIva(BigDecimal.ZERO)
                            .subtotalExentoIva(BigDecimal.ZERO)
                            .totalDescuento(BigDecimal.ZERO)
                            .totalIce(BigDecimal.ZERO)
                            .totalIva(BigDecimal.ZERO)
                            .propina(BigDecimal.ZERO)
                            .total(total)
                            .moneda("DOLAR")
                            .idMembresia(command.idMembresia())
                            .idVenta(command.idVenta())
                            .estado("GENERADO")
                            .idUsuarioRegistro(command.idUsuarioRegistro())
                            .build();

                    return comprobanteRepository.save(comprobante);
                });
    }

    @Override
    public Mono<Comprobante> buscarPorId(Long id, Integer idCompania) {
        return comprobanteRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Comprobante", id)))
                .flatMap(comprobante -> {
                    if (!comprobante.getIdCompania().equals(idCompania)) {
                        return Mono.error(new NotFoundException("Comprobante", id));
                    }
                    return Mono.just(comprobante);
                });
    }

    @Override
    public Flux<Comprobante> listarPorEmpresa(Integer idCompania, Integer idSucursal, String estado, int page, int limit) {
        int offset = (page - 1) * limit;
        return comprobanteRepository.findByEmpresa(idCompania, idSucursal, estado, offset, limit);
    }

    @Override
    public Mono<Long> contarPorEmpresa(Integer idCompania, Integer idSucursal, String estado) {
        return comprobanteRepository.countByEmpresa(idCompania, idSucursal, estado);
    }

    @Override
    public Mono<Comprobante> procesarEnvioSri(Long id, Integer idCompania, Integer idSucursal) {
        return envioSriService.procesarComprobante(id, idCompania, idSucursal);
    }
}
