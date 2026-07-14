package com.gymadmin.billing.application.service;

import com.gymadmin.billing.application.command.EmitirFacturaCommand;
import com.gymadmin.billing.domain.model.ClaveAcceso;
import com.gymadmin.billing.domain.model.Comprobante;
import com.gymadmin.billing.domain.model.ComprobanteDetalle;
import com.gymadmin.billing.domain.model.ConfigSri;
import com.gymadmin.billing.domain.port.in.ComprobanteUseCase;
import com.gymadmin.billing.domain.port.out.CertificadoRepository;
import com.gymadmin.billing.domain.port.out.ComprobanteRepository;
import com.gymadmin.billing.domain.port.out.ConfigSriRepository;
import com.gymadmin.billing.domain.port.out.EmailNotificationPort;
import com.gymadmin.billing.domain.port.out.FileStoragePort;
import com.gymadmin.billing.domain.port.out.SecuencialRepository;
import com.gymadmin.billing.domain.port.out.XmlSignaturePort;
import com.gymadmin.billing.infrastructure.adapter.out.xml.FacturaXmlBuilder;
import com.gymadmin.billing.infrastructure.exception.BusinessException;
import com.gymadmin.billing.infrastructure.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComprobanteService implements ComprobanteUseCase {

    /**
     * Tipo de comprobante SRI para facturas. Es el valor específico de esta
     * operación ({@link #emitirFactura}), no un lookup contra el catálogo
     * {@code sri.tipos_comprobante}.
     */
    private static final String TIPO_COMPROBANTE_FACTURA = "01";

    /** G10 · Umbral sobre el cual el SRI exige bancarizar el excedente. */
    private static final BigDecimal UMBRAL_BANCARIZACION = new BigDecimal("500.00");

    private final ComprobanteRepository comprobanteRepository;
    private final ConfigSriRepository configSriRepository;
    private final CertificadoRepository certificadoRepository;
    private final XmlSignaturePort xmlSignaturePort;
    private final FacturaXmlBuilder facturaXmlBuilder;
    private final EnvioSriService envioSriService;
    private final FileStoragePort fileStoragePort;
    private final EmailNotificationPort emailNotificationPort;
    private final SecuencialRepository secuencialRepository;
    private final CatalogoSriService catalogoSriService;

    @Override
    public Mono<Comprobante> emitirFactura(EmitirFacturaCommand command) {
        return validarCatalogosSri(command)
                .then(configSriRepository.findByEmpresa(command.idCompania(), command.idSucursal()))
                .switchIfEmpty(Mono.error(new NotFoundException("Configuración SRI no encontrada para la empresa")))
                .flatMap(config -> secuencialRepository.reservarSiguiente(
                                command.idCompania(),
                                command.idSucursal(),
                                command.codEstablecimiento(),
                                command.codPuntoEmision(),
                                TIPO_COMPROBANTE_FACTURA)
                        .map(nextSeq -> String.format("%09d", nextSeq))
                        .flatMap(secuencial -> {
                            ClaveAcceso claveAcceso = ClaveAcceso.generar(
                                    command.fechaEmision(),
                                    TIPO_COMPROBANTE_FACTURA,
                                    config.getRuc(),
                                    config.getAmbiente(),
                                    command.codEstablecimiento(),
                                    command.codPuntoEmision(),
                                    secuencial,
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
                                    .tipoComprobante(TIPO_COMPROBANTE_FACTURA)
                                    .claveAcceso(claveAcceso.getValue())
                                    .codEstablecimiento(command.codEstablecimiento())
                                    .codPuntoEmision(command.codPuntoEmision())
                                    .secuencial(secuencial)
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

                            return comprobanteRepository.save(comprobante)
                                    .flatMap(saved -> transmitirInmediatamente(saved, command, config));
                        }));
    }

    /**
     * G2 · Transmisión inmediata al SRI dentro del propio POST. Ver
     * {@link EnvioSriService#procesarEmisionInmediata(Comprobante, List, List, ConfigSri)}.
     * <p>
     * El controller siempre devuelve HTTP 201 con el {@link Comprobante} tal
     * como quedó (AUTORIZADO / DEVUELTO / NO_AUTORIZADO / ERROR). Solo se
     * devuelve un 5xx si falla la persistencia inicial (arriba en el pipeline).
     */
    private Mono<Comprobante> transmitirInmediatamente(Comprobante saved, EmitirFacturaCommand command, ConfigSri configSri) {
        List<ComprobanteDetalle> detalles = command.detalles().stream()
                .map(d -> ComprobanteDetalle.builder()
                        .idComprobante(saved.getId())
                        .codigoPrincipal(d.codigoPrincipal())
                        .codigoAuxiliar(d.codigoAuxiliar())
                        .descripcion(d.descripcion())
                        .cantidad(d.cantidad())
                        .precioUnitario(d.precioUnitario())
                        .descuento(d.descuento())
                        .precioTotalSinImpuesto(d.precioTotalSinImpuesto())
                        .orden(d.orden())
                        .build())
                .toList();

        List<FacturaXmlBuilder.Pago> pagos = command.pagos().stream()
                .map(p -> new FacturaXmlBuilder.Pago(p.formaPago(), p.total()))
                .toList();

        return envioSriService.procesarEmisionInmediata(saved, detalles, pagos, configSri)
                .onErrorResume(e -> {
                    // Doble red de seguridad: procesarEmisionInmediata ya captura sus
                    // errores, pero si por algún motivo escapa uno lo persistimos y
                    // devolvemos el comprobante en estado ERROR para no fallar el 201.
                    log.error("Fallo inesperado en pipeline síncrono para comprobante {}: {}", saved.getId(), e.getMessage());
                    return Mono.just(saved);
                });
    }

    /**
     * Ejecuta las validaciones semánticas contra los catálogos SRI en paralelo:
     * <ul>
     *   <li>{@code tipoIdReceptor} debe existir en {@code sri.tipos_identificacion_comprador}.</li>
     *   <li>Cada {@code pagos[].formaPago} debe existir en {@code sri.formas_pago}.</li>
     * </ul>
     * TODO(G6-follow): validar {@code codigoTarifaIva} por detalle cuando el
     * DTO/command exponga ese campo (hoy la tarifa está hardcodeada y este
     * valor no llega desde el request).
     */
    private Mono<Void> validarCatalogosSri(EmitirFacturaCommand command) {
        Mono<Void> validTipoId = catalogoSriService.existeTipoIdentificacion(command.tipoIdReceptor())
                .flatMap(existe -> existe
                        ? Mono.<Void>empty()
                        : Mono.error(new BusinessException(
                                "Tipo de identificación no reconocido: " + command.tipoIdReceptor())));

        java.util.List<String> formasPago = command.pagos().stream()
                .map(EmitirFacturaCommand.PagoItem::formaPago)
                .distinct()
                .collect(Collectors.toList());

        Mono<Void> validFormasPago = Flux.fromIterable(formasPago)
                .flatMap(codigo -> catalogoSriService.existeFormaPago(codigo)
                        .flatMap(existe -> existe
                                ? Mono.<Void>empty()
                                : Mono.error(new BusinessException(
                                        "Forma de pago no reconocida: " + codigo))))
                .then()
                // Solo tiene sentido evaluar el flag `bancarizada` de códigos que existen.
                .then(validarBancarizacion(command));

        return Mono.when(validTipoId, validFormasPago);
    }

    /**
     * G10 · Bancarización. Si el total del comprobante supera
     * {@value #UMBRAL_BANCARIZACION} USD, el SRI exige que el <strong>excedente</strong>
     * sobre ese umbral se pague por un medio que utilice el sistema financiero
     * ({@code sri.formas_pago.bancarizada}).
     * <p>
     * Se valida el excedente y no el total, de modo que un pago mixto sigue siendo
     * legal: en una factura de USD 600 se pueden pagar 100 en efectivo siempre que
     * al menos 500 vayan por un medio bancarizado. Emitir con un excedente no
     * bancarizado autorizaría en el SRI pero abriría una glosa en el cruce automático.
     */
    private Mono<Void> validarBancarizacion(EmitirFacturaCommand command) {
        // Mismo cálculo que usa emitirFactura para el total del comprobante.
        BigDecimal total = command.pagos().stream()
                .map(EmitirFacturaCommand.PagoItem::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (total.compareTo(UMBRAL_BANCARIZACION) <= 0) {
            return Mono.empty();
        }

        BigDecimal excedente = total.subtract(UMBRAL_BANCARIZACION);

        return Flux.fromIterable(command.pagos())
                .filterWhen(pago -> catalogoSriService.esBancarizada(pago.formaPago()))
                .map(EmitirFacturaCommand.PagoItem::total)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .flatMap(bancarizado -> bancarizado.compareTo(excedente) >= 0
                        ? Mono.<Void>empty()
                        : Mono.<Void>error(new BusinessException(
                                ("El total de %s USD supera los %s USD: al menos %s USD deben pagarse "
                                        + "con una forma de pago bancarizada (códigos 16, 17, 18, 19, 20). "
                                        + "Bancarizado actualmente: %s USD.")
                                        .formatted(total.toPlainString(),
                                                UMBRAL_BANCARIZACION.toPlainString(),
                                                excedente.toPlainString(),
                                                bancarizado.toPlainString()))));
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

    @Override
    public Mono<String> leerXmlFirmado(Long id, Integer idCompania) {
        return buscarPorId(id, idCompania)
                .flatMap(comprobante -> {
                    if (comprobante.getXmlFirmadoPath() == null) {
                        return Mono.error(new NotFoundException("XML firmado no disponible para el comprobante: " + id));
                    }
                    return fileStoragePort.readFile(comprobante.getXmlFirmadoPath());
                });
    }

    @Override
    public Mono<byte[]> leerRidePdf(Long id, Integer idCompania) {
        return buscarPorId(id, idCompania)
                .flatMap(comprobante -> {
                    if (comprobante.getRidePdfPath() == null) {
                        return Mono.error(new NotFoundException("RIDE PDF no disponible para el comprobante: " + id));
                    }
                    return fileStoragePort.readFileBytes(comprobante.getRidePdfPath());
                });
    }

    @Override
    public Mono<Void> reenviarEmail(Long id, Integer idCompania) {
        return buscarPorId(id, idCompania)
                .flatMap(comprobante -> {
                    if (comprobante.getRidePdfPath() == null) {
                        return Mono.error(new NotFoundException("RIDE PDF no disponible para el comprobante: " + id));
                    }
                    return fileStoragePort.readFileBytes(comprobante.getRidePdfPath())
                            .flatMap(pdfBytes -> emailNotificationPort.enviarFactura(comprobante, pdfBytes));
                });
    }
}
