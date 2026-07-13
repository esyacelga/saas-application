package com.gymadmin.billing.application.service;

import com.gymadmin.billing.application.command.EmitirFacturaCommand;
import com.gymadmin.billing.application.command.EmitirNotaCreditoCommand;
import com.gymadmin.billing.domain.model.ClaveAcceso;
import com.gymadmin.billing.domain.model.Comprobante;
import com.gymadmin.billing.domain.model.ComprobanteDetalle;
import com.gymadmin.billing.domain.model.ConfigSri;
import com.gymadmin.billing.domain.model.NotaCreditoReferencia;
import com.gymadmin.billing.domain.model.sri.MotivoAnulacionNcSri;
import com.gymadmin.billing.domain.port.in.NotaCreditoUseCase;
import com.gymadmin.billing.domain.port.out.ComprobanteRepository;
import com.gymadmin.billing.domain.port.out.ConfigSriRepository;
import com.gymadmin.billing.domain.port.out.NotaCreditoReferenciaRepository;
import com.gymadmin.billing.domain.port.out.SecuencialRepository;
import com.gymadmin.billing.infrastructure.adapter.out.xml.FacturaXmlBuilder;
import com.gymadmin.billing.infrastructure.adapter.out.xml.NotaCreditoXmlBuilder;
import com.gymadmin.billing.infrastructure.exception.BusinessException;
import com.gymadmin.billing.infrastructure.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Caso de uso G4 · Notas de crédito electrónicas (tipo SRI {@code "04"}).
 * <p>
 * Ciclo emisión:
 * <ol>
 *     <li>Validar la factura original (existe, misma compañía, estado
 *         {@code AUTORIZADO}, tipo {@code "01"}).</li>
 *     <li>Validar el motivo contra {@code sri.motivos_anulacion_nc}.</li>
 *     <li>Reservar secuencial atómico con {@code SecuencialRepository} (tipo {@code "04"}).</li>
 *     <li>Generar clave de acceso 49 dígitos con tipo comprobante {@code "04"}.</li>
 *     <li>Persistir NC en {@code facturacion.comprobantes} apuntando a la
 *         factura original vía {@code id_comprobante_ref}.</li>
 *     <li>Persistir referencia en {@code facturacion.notas_credito_referencias}.</li>
 *     <li>Disparar el pipeline síncrono G2 con el XML de NC v1.1.0.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotaCreditoService implements NotaCreditoUseCase {

    /** Tipo SRI de nota de crédito. */
    private static final String TIPO_COMPROBANTE_NC = "04";
    /** Tipo SRI de factura. Solo se admiten NC sobre facturas hoy. */
    private static final String TIPO_COMPROBANTE_FACTURA = "01";
    /** Estado terminal exigido a la factura original para poder emitir NC. */
    private static final String ESTADO_AUTORIZADO = "AUTORIZADO";

    private final ComprobanteRepository comprobanteRepository;
    private final ConfigSriRepository configSriRepository;
    private final SecuencialRepository secuencialRepository;
    private final CatalogoSriService catalogoSriService;
    private final NotaCreditoReferenciaRepository notaCreditoReferenciaRepository;
    private final NotaCreditoXmlBuilder notaCreditoXmlBuilder;
    private final EnvioSriService envioSriService;

    @Override
    public Mono<Comprobante> emitirNotaCredito(EmitirNotaCreditoCommand command) {
        return validarFacturaOriginal(command.idFacturaOriginal(), command.idCompania(), command.valorModificacion())
                .flatMap(facturaOriginal -> catalogoSriService.obtenerMotivoAnulacion(command.codigoMotivo())
                        .zipWith(configSriRepository.findByEmpresa(command.idCompania(), command.idSucursal())
                                .switchIfEmpty(Mono.error(new NotFoundException(
                                        "Configuración SRI no encontrada para la empresa")))
                        )
                        .flatMap(tuple -> {
                            MotivoAnulacionNcSri motivo = tuple.getT1();
                            ConfigSri configSri = tuple.getT2();
                            return reservarSecuencialYPersistir(command, facturaOriginal, motivo, configSri);
                        }));
    }

    private Mono<Comprobante> validarFacturaOriginal(Long idFacturaOriginal, Integer idCompania, BigDecimal valorModificacion) {
        return comprobanteRepository.findById(idFacturaOriginal)
                .switchIfEmpty(Mono.error(new NotFoundException("Factura original", idFacturaOriginal)))
                .flatMap(factura -> {
                    if (!factura.getIdCompania().equals(idCompania)) {
                        // Multi-tenancy: nunca revelar que existe una factura de otra compañía.
                        return Mono.error(new NotFoundException("Factura original", idFacturaOriginal));
                    }
                    if (!TIPO_COMPROBANTE_FACTURA.equals(factura.getTipoComprobante())) {
                        return Mono.error(new BusinessException(
                                "Solo se puede emitir NC sobre facturas (tipo 01); el comprobante "
                                        + idFacturaOriginal + " es tipo " + factura.getTipoComprobante()));
                    }
                    if (!ESTADO_AUTORIZADO.equals(factura.getEstado())) {
                        return Mono.error(new BusinessException(
                                "Solo facturas AUTORIZADO admiten NC; la factura " + idFacturaOriginal
                                        + " está en estado " + factura.getEstado()));
                    }
                    BigDecimal totalFactura = factura.getTotal() != null ? factura.getTotal() : BigDecimal.ZERO;
                    if (valorModificacion == null || valorModificacion.signum() <= 0) {
                        return Mono.error(new BusinessException(
                                "valor_modificacion debe ser positivo"));
                    }
                    if (valorModificacion.compareTo(totalFactura) > 0) {
                        return Mono.error(new BusinessException(
                                "valor_modificacion (" + valorModificacion + ") no puede exceder el total de la factura original ("
                                        + totalFactura + ")"));
                    }
                    return Mono.just(factura);
                });
    }

    private Mono<Comprobante> reservarSecuencialYPersistir(EmitirNotaCreditoCommand command,
                                                            Comprobante facturaOriginal,
                                                            MotivoAnulacionNcSri motivo,
                                                            ConfigSri configSri) {
        return secuencialRepository.reservarSiguiente(
                        command.idCompania(),
                        command.idSucursal(),
                        command.codEstablecimiento(),
                        command.codPuntoEmision(),
                        TIPO_COMPROBANTE_NC)
                .map(nextSeq -> String.format("%09d", nextSeq))
                .flatMap(secuencial -> {
                    ClaveAcceso claveAcceso = ClaveAcceso.generar(
                            command.fechaEmision(),
                            TIPO_COMPROBANTE_NC,
                            configSri.getRuc(),
                            configSri.getAmbiente(),
                            command.codEstablecimiento(),
                            command.codPuntoEmision(),
                            secuencial,
                            command.codigoNumerico()
                    );

                    BigDecimal subtotalSinImpuesto = command.detalles().stream()
                            .map(EmitirFacturaCommand.DetalleFacturaItem::precioTotalSinImpuesto)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    Comprobante nc = Comprobante.builder()
                            .idCompania(command.idCompania())
                            .idSucursal(command.idSucursal())
                            .tipoComprobante(TIPO_COMPROBANTE_NC)
                            .claveAcceso(claveAcceso.getValue())
                            .codEstablecimiento(command.codEstablecimiento())
                            .codPuntoEmision(command.codPuntoEmision())
                            .secuencial(secuencial)
                            .fechaEmision(command.fechaEmision())
                            .ambiente(configSri.getAmbiente())
                            // Receptor copiado de la factura original — la NC va al mismo cliente.
                            .tipoIdReceptor(facturaOriginal.getTipoIdReceptor())
                            .idReceptor(facturaOriginal.getIdReceptor())
                            .razonSocialReceptor(facturaOriginal.getRazonSocialReceptor())
                            .emailReceptor(facturaOriginal.getEmailReceptor())
                            .direccionReceptor(facturaOriginal.getDireccionReceptor())
                            .telefonoReceptor(facturaOriginal.getTelefonoReceptor())
                            .subtotalSinImpuesto(subtotalSinImpuesto)
                            .subtotalIva0(BigDecimal.ZERO)
                            .subtotalNoObjetoIva(BigDecimal.ZERO)
                            .subtotalExentoIva(BigDecimal.ZERO)
                            .totalDescuento(BigDecimal.ZERO)
                            .totalIce(BigDecimal.ZERO)
                            .totalIva(BigDecimal.ZERO)
                            .propina(BigDecimal.ZERO)
                            // Total de la NC = valor del ajuste (base del command).
                            .total(command.valorModificacion())
                            .moneda("DOLAR")
                            // Vínculo a la factura original.
                            .idComprobanteRef(facturaOriginal.getId())
                            .estado("GENERADO")
                            .idUsuarioRegistro(command.idUsuarioRegistro())
                            .build();

                    return comprobanteRepository.save(nc)
                            .flatMap(saved -> persistirReferenciaYTransmitir(
                                    saved, command, facturaOriginal, motivo, configSri));
                });
    }

    private Mono<Comprobante> persistirReferenciaYTransmitir(Comprobante nc,
                                                              EmitirNotaCreditoCommand command,
                                                              Comprobante facturaOriginal,
                                                              MotivoAnulacionNcSri motivo,
                                                              ConfigSri configSri) {
        String numDocModificado = String.format("%s-%s-%s",
                facturaOriginal.getCodEstablecimiento(),
                facturaOriginal.getCodPuntoEmision(),
                facturaOriginal.getSecuencial());

        NotaCreditoReferencia referencia = NotaCreditoReferencia.builder()
                .idCompania(command.idCompania())
                .idSucursal(command.idSucursal())
                .idComprobante(nc.getId())
                .codDocModificado(TIPO_COMPROBANTE_FACTURA)
                .numDocModificado(numDocModificado)
                .fechaEmisionModif(facturaOriginal.getFechaEmision())
                .idMotivoAnulacion(motivo.id())
                .razon(command.razon())
                .valorModificado(command.valorModificacion())
                .build();

        return notaCreditoReferenciaRepository.save(referencia)
                .flatMap(savedRef -> transmitirInmediatamente(nc, command, savedRef, configSri));
    }

    /**
     * Dispara el pipeline síncrono G2 con el XML de NC v1.1.0 construido en
     * memoria. Reutiliza {@link EnvioSriService#procesarEmisionInmediataConXml}
     * — mismo timeout, mismo backoff, misma semántica de encolado en fallo.
     */
    private Mono<Comprobante> transmitirInmediatamente(Comprobante nc,
                                                        EmitirNotaCreditoCommand command,
                                                        NotaCreditoReferencia referencia,
                                                        ConfigSri configSri) {
        List<ComprobanteDetalle> detalles = command.detalles().stream()
                .map(d -> ComprobanteDetalle.builder()
                        .idComprobante(nc.getId())
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

        List<FacturaXmlBuilder.ImpuestoTotal> impuestosTotales = buildImpuestosTotales(detalles);
        String xmlSinFirmar = notaCreditoXmlBuilder.buildXml(nc, detalles, configSri, referencia, impuestosTotales);

        return envioSriService.procesarEmisionInmediataConXml(nc, xmlSinFirmar)
                .onErrorResume(e -> {
                    log.error("Fallo inesperado en pipeline síncrono de NC {}: {}", nc.getId(), e.getMessage());
                    return Mono.just(nc);
                });
    }

    /**
     * Agrega la base imponible y calcula IVA 15% en un solo registro (código
     * {@code "2"}, porcentaje {@code "4"}). Idéntico al hardcode actual de la
     * factura — se ajustará cuando G6 exponga la tarifa por detalle.
     */
    private List<FacturaXmlBuilder.ImpuestoTotal> buildImpuestosTotales(List<ComprobanteDetalle> detalles) {
        BigDecimal baseImponible = detalles.stream()
                .map(d -> d.getPrecioTotalSinImpuesto() != null ? d.getPrecioTotalSinImpuesto() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal valorIva = baseImponible
                .multiply(new BigDecimal("0.15"))
                .setScale(2, RoundingMode.HALF_UP);
        return List.of(new FacturaXmlBuilder.ImpuestoTotal(
                "2",
                "4",
                baseImponible.setScale(2, RoundingMode.HALF_UP),
                valorIva));
    }

    @Override
    public Mono<Comprobante> buscarPorId(Long id, Integer idCompania) {
        return comprobanteRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Nota de crédito", id)))
                .flatMap(c -> {
                    // Multi-tenancy: no revelar cross-tenant.
                    if (!c.getIdCompania().equals(idCompania)) {
                        return Mono.error(new NotFoundException("Nota de crédito", id));
                    }
                    if (!TIPO_COMPROBANTE_NC.equals(c.getTipoComprobante())) {
                        // El ID existe pero no es una NC — desde este caso de uso equivale a not-found.
                        return Mono.error(new NotFoundException("Nota de crédito", id));
                    }
                    return Mono.just(c);
                });
    }

    @Override
    public Flux<Comprobante> listar(Integer idCompania, Integer idSucursal, String estado,
                                    Long idFacturaOriginal, int page, int limit) {
        int offset = (page - 1) * limit;
        return comprobanteRepository.findByEmpresaAndTipo(
                idCompania, idSucursal, TIPO_COMPROBANTE_NC, estado, idFacturaOriginal, offset, limit);
    }

    @Override
    public Mono<Long> contar(Integer idCompania, Integer idSucursal, String estado, Long idFacturaOriginal) {
        return comprobanteRepository.countByEmpresaAndTipo(
                idCompania, idSucursal, TIPO_COMPROBANTE_NC, estado, idFacturaOriginal);
    }
}
