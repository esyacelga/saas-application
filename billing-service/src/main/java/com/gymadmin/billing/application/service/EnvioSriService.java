package com.gymadmin.billing.application.service;

import com.gymadmin.billing.domain.model.ColaEnvio;
import com.gymadmin.billing.domain.model.Comprobante;
import com.gymadmin.billing.domain.model.ComprobanteDetalle;
import com.gymadmin.billing.domain.model.ConfigSri;
import com.gymadmin.billing.domain.model.EnvioSri;
import com.gymadmin.billing.domain.port.out.CertificadoRepository;
import com.gymadmin.billing.domain.port.out.ColaEnvioRepository;
import com.gymadmin.billing.domain.port.out.ComprobanteRepository;
import com.gymadmin.billing.domain.port.out.ConfigSriRepository;
import com.gymadmin.billing.domain.port.out.EmailNotificationPort;
import com.gymadmin.billing.domain.port.out.EnvioSriRepository;
import com.gymadmin.billing.domain.port.out.FileStoragePort;
import com.gymadmin.billing.domain.port.out.RidePdfPort;
import com.gymadmin.billing.domain.port.out.SriSoapPort;
import com.gymadmin.billing.domain.port.out.XmlSignaturePort;
import com.gymadmin.billing.infrastructure.adapter.out.xml.FacturaXmlBuilder;
import com.gymadmin.billing.infrastructure.config.SriTimeoutProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
public class EnvioSriService {

    private static final long[] RETRY_DELAYS_MINUTES = {1, 5, 15, 60, 240};

    private final ComprobanteRepository comprobanteRepository;
    private final EnvioSriRepository envioSriRepository;
    private final ColaEnvioRepository colaEnvioRepository;
    private final SriSoapPort sriSoapPort;
    private final CertificadoRepository certificadoRepository;
    private final XmlSignaturePort xmlSignaturePort;
    private final FacturaXmlBuilder facturaXmlBuilder;
    private final FileStoragePort fileStoragePort;
    private final RidePdfPort ridePdfPort;
    private final EmailNotificationPort emailNotificationPort;
    private final ConfigSriRepository configSriRepository;
    private final SriTimeoutProperties sriTimeoutProperties;
    private final Counter comprobantesEmitidosFactura;
    private final Counter comprobantesEmitidosNotaCredito;
    private final Counter comprobantesAutorizados;
    private final Counter comprobantesErroresSri;
    private final Counter comprobantesReintentos;
    private final Timer sriEmisionDuracion;
    private final Counter sriEmisionTimeouts;

    public EnvioSriService(
            ComprobanteRepository comprobanteRepository,
            EnvioSriRepository envioSriRepository,
            ColaEnvioRepository colaEnvioRepository,
            SriSoapPort sriSoapPort,
            CertificadoRepository certificadoRepository,
            XmlSignaturePort xmlSignaturePort,
            FacturaXmlBuilder facturaXmlBuilder,
            FileStoragePort fileStoragePort,
            RidePdfPort ridePdfPort,
            EmailNotificationPort emailNotificationPort,
            ConfigSriRepository configSriRepository,
            SriTimeoutProperties sriTimeoutProperties,
            @Qualifier("comprobantesEmitidosFactura") Counter comprobantesEmitidosFactura,
            @Qualifier("comprobantesEmitidosNotaCredito") Counter comprobantesEmitidosNotaCredito,
            @Qualifier("comprobantesAutorizados") Counter comprobantesAutorizados,
            @Qualifier("comprobantesErroresSri") Counter comprobantesErroresSri,
            @Qualifier("comprobantesReintentos") Counter comprobantesReintentos,
            @Qualifier("sriEmisionDuracion") Timer sriEmisionDuracion,
            @Qualifier("sriEmisionTimeouts") Counter sriEmisionTimeouts) {
        this.comprobanteRepository = comprobanteRepository;
        this.envioSriRepository = envioSriRepository;
        this.colaEnvioRepository = colaEnvioRepository;
        this.sriSoapPort = sriSoapPort;
        this.certificadoRepository = certificadoRepository;
        this.xmlSignaturePort = xmlSignaturePort;
        this.facturaXmlBuilder = facturaXmlBuilder;
        this.fileStoragePort = fileStoragePort;
        this.ridePdfPort = ridePdfPort;
        this.emailNotificationPort = emailNotificationPort;
        this.configSriRepository = configSriRepository;
        this.sriTimeoutProperties = sriTimeoutProperties;
        this.comprobantesEmitidosFactura = comprobantesEmitidosFactura;
        this.comprobantesEmitidosNotaCredito = comprobantesEmitidosNotaCredito;
        this.comprobantesAutorizados = comprobantesAutorizados;
        this.comprobantesErroresSri = comprobantesErroresSri;
        this.comprobantesReintentos = comprobantesReintentos;
        this.sriEmisionDuracion = sriEmisionDuracion;
        this.sriEmisionTimeouts = sriEmisionTimeouts;
    }

    /**
     * Entrada usada por {@code POST /{id}/enviar} y por
     * {@link RetrySchedulerService}. Carga detalles y ConfigSri desde BD, ya
     * que en el path de reintentos el {@code EmitirFacturaCommand} original
     * ya no existe.
     * <p>
     * <b>No aplica timeout</b>: mantiene el comportamiento asíncrono actual y
     * respeta el backoff {@code {1,5,15,60,240} min}. Encola en {@code cola_envio}
     * cuando el envío falla.
     */
    public Mono<Comprobante> procesarComprobante(Long idComprobante, Integer idCompania, Integer idSucursal) {
        return comprobanteRepository.findById(idComprobante)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Comprobante no encontrado: " + idComprobante)))
                .flatMap(comprobante -> buildXmlDesdeBD(comprobante)
                        .flatMap(xmlSinFirmar -> firmarYEnviar(
                                comprobante,
                                xmlSinFirmar,
                                idCompania,
                                idSucursal,
                                null,
                                false)));
    }

    /**
     * G2 · Transmisión inmediata. Pipeline síncrono
     * {@code firma → envío → autorización → RIDE} disparado desde
     * {@code POST /api/v1/comprobantes/facturas}.
     * <p>
     * A diferencia de {@link #procesarComprobante(Long, Integer, Integer)}:
     * <ul>
     *     <li>Aplica timeout ({@code sri.timeout.envio-seconds}, default 15s).</li>
     *     <li>No lee detalles ni ConfigSri desde BD (se pasan como parámetro).</li>
     *     <li>Si timeout / error → estado {@code ERROR} + fila en
     *         {@code cola_envio} con {@code proxima_ejecucion = now()}.</li>
     *     <li>Si SRI responde {@code DEVUELTO} / {@code NO_AUTORIZADO} →
     *         fila en {@code cola_envio} con backoff {@code {1,5,15,60,240} min}
     *         (mismo comportamiento actual).</li>
     *     <li>Si {@code AUTORIZADO} → no crea fila en {@code cola_envio}.</li>
     * </ul>
     * <p>
     * <b>Nunca lanza</b>: en cualquier fallo del pipeline devuelve un
     * {@link Comprobante} con el estado transitorio ({@code ERROR},
     * {@code DEVUELTO}, {@code NO_AUTORIZADO}), para que el controller
     * responda HTTP 201 siempre (la factura ya está persistida).
     */
    public Mono<Comprobante> procesarEmisionInmediata(
            Comprobante comprobante,
            List<ComprobanteDetalle> detalles,
            List<FacturaXmlBuilder.Pago> pagos,
            ConfigSri configSri) {

        return procesarEmisionInmediataConXml(comprobante,
                buildXmlInmediato(comprobante, detalles, pagos, configSri));
    }

    /**
     * G2 · Variante que acepta el XML ya construido. Se usa desde flujos que
     * emiten un tipo de comprobante distinto a factura (ej. Nota de crédito
     * tipo {@code "04"}), donde el llamador es quien elige el builder correcto
     * ({@link com.gymadmin.billing.infrastructure.adapter.out.xml.NotaCreditoXmlBuilder}).
     * <p>
     * Semántica idéntica a
     * {@link #procesarEmisionInmediata(Comprobante, List, List, ConfigSri)}:
     * aplica timeout, encola solo en fallo, nunca lanza.
     */
    public Mono<Comprobante> procesarEmisionInmediataConXml(
            Comprobante comprobante,
            String xmlSinFirmar) {

        Duration timeout = sriTimeoutProperties.getEnvioDuration();
        long startNanos = System.nanoTime();

        return Mono.just(xmlSinFirmar)
                .flatMap(xml -> firmarYEnviar(
                        comprobante,
                        xml,
                        comprobante.getIdCompania(),
                        comprobante.getIdSucursal(),
                        timeout,
                        true))
                .doOnSuccess(c -> sriEmisionDuracion.record(Duration.ofNanos(System.nanoTime() - startNanos)))
                .onErrorResume(TimeoutException.class, e -> handleTimeoutInmediato(comprobante, e, startNanos))
                .onErrorResume(e -> handleErrorInmediato(comprobante, e, startNanos));
    }

    /**
     * Pipeline reutilizable {@code firma → envío → autorización → RIDE}.
     *
     * @param comprobante          comprobante ya persistido en estado GENERADO.
     * @param xmlSinFirmar         XML a firmar (real, no placeholder).
     * @param idCompania           tenant scope.
     * @param idSucursal           tenant scope.
     * @param timeout              si no es {@code null}, se aplica al pipeline completo.
     * @param encolarSoloEnFallo   {@code true} para la ruta síncrona (G2): la
     *                             fila en {@code cola_envio} solo se crea si
     *                             hay fallo (timeout, error red, DEVUELTO,
     *                             NO_AUTORIZADO). {@code false} preserva el
     *                             comportamiento actual (crea la fila desde el
     *                             inicio en estado {@code PENDIENTE}).
     */
    private Mono<Comprobante> firmarYEnviar(
            Comprobante comprobante,
            String xmlSinFirmar,
            Integer idCompania,
            Integer idSucursal,
            Duration timeout,
            boolean encolarSoloEnFallo) {

        Mono<Comprobante> pipeline = Mono.zip(
                certificadoRepository.getActiveCertificateContent(idCompania, idSucursal),
                certificadoRepository.getActiveCertificatePassword(idCompania, idSucursal)
        )
        .flatMap(tuple -> {
            byte[] certContent = tuple.getT1();
            String certPassword = tuple.getT2();
            return xmlSignaturePort.sign(xmlSinFirmar, certContent, certPassword);
        })
        .flatMap(xmlFirmado -> comprobanteRepository.updateEstado(
                comprobante.getId(), "FIRMADO", null, null, null, null, null
        ).map(c -> Tuples.of(xmlFirmado, c)))
        .flatMap(tuple -> {
            String xmlFirmado = tuple.getT1();
            Comprobante updated = tuple.getT2();
            incrementarContadorEmision(updated);
            if (encolarSoloEnFallo) {
                // G2: sintético en memoria (intentos=0). Se persiste solo si falla.
                ColaEnvio virtual = ColaEnvio.builder()
                        .idComprobante(updated.getId())
                        .idCompania(idCompania)
                        .idSucursal(idSucursal)
                        .estado("PENDIENTE")
                        .intentos((short) 0)
                        .maxIntentos((short) 5)
                        .proximaEjecucion(OffsetDateTime.now())
                        .build();
                return Mono.just(Tuples.of(xmlFirmado, updated, virtual));
            }
            ColaEnvio cola = ColaEnvio.builder()
                    .idComprobante(updated.getId())
                    .idCompania(idCompania)
                    .idSucursal(idSucursal)
                    .estado("PENDIENTE")
                    .intentos((short) 0)
                    .maxIntentos((short) 5)
                    .proximaEjecucion(OffsetDateTime.now())
                    .build();
            return colaEnvioRepository.save(cola)
                    .map(c -> Tuples.of(xmlFirmado, updated, c));
        })
        .flatMap(tuple -> enviarASri(tuple.getT1(), tuple.getT2(), tuple.getT3(), comprobante.getAmbiente(), encolarSoloEnFallo));

        return timeout != null ? pipeline.timeout(timeout) : pipeline;
    }

    private Mono<Comprobante> enviarASri(String xmlFirmado, Comprobante comprobante, ColaEnvio cola, String ambiente,
                                          boolean encolarSoloEnFallo) {
        String xmlBase64 = java.util.Base64.getEncoder()
                .encodeToString(xmlFirmado.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        String endpointUrl = resolveRecepcionEndpoint(ambiente);

        Mono<Comprobante> flow = sriSoapPort.enviarComprobante(xmlBase64, ambiente)
                .flatMap(respuestaRecepcion -> {
                    boolean exitoso = "RECIBIDA".equals(respuestaRecepcion.getEstado());
                    String mensajeError = respuestaRecepcion.getMensajes() != null
                            ? String.join("; ", respuestaRecepcion.getMensajes()) : null;

                    EnvioSri envio = EnvioSri.builder()
                            .idComprobante(comprobante.getId())
                            .idCompania(comprobante.getIdCompania())
                            .idSucursal(comprobante.getIdSucursal())
                            .tipoOperacion("RECEPCION")
                            .endpointUrl(endpointUrl)
                            .requestSoap(xmlFirmado)
                            .exitoso(exitoso)
                            .estadoSri(respuestaRecepcion.getEstado())
                            .mensajeError(exitoso ? null : mensajeError)
                            .intentoNumero((short) (cola.getIntentos() != null ? cola.getIntentos() + 1 : 1))
                            .build();

                    if (exitoso) {
                        return envioSriRepository.save(envio)
                                .then(comprobanteRepository.updateEstado(
                                        comprobante.getId(), "RECIBIDO", null, null, null, null, null))
                                .flatMap(c -> autorizarComprobante(c, cola, ambiente, xmlFirmado, encolarSoloEnFallo));
                    } else {
                        comprobantesErroresSri.increment();
                        return envioSriRepository.save(envio)
                                .then(scheduleRetry(cola, respuestaRecepcion.getMensajes(), encolarSoloEnFallo, false))
                                .then(comprobanteRepository.updateEstado(
                                        comprobante.getId(), "DEVUELTO", null, null, null, null, null));
                    }
                });

        // En el path async ({@code /enviar} + scheduler) mantenemos el
        // .onErrorResume histórico: cualquier excepción se persiste como ERROR
        // + fila en cola. En el path síncrono (G2) NO capturamos aquí: la
        // excepción sube al onErrorResume de procesarEmisionInmediata para que
        // el timeout/TimeoutException se distinga limpiamente.
        if (encolarSoloEnFallo) {
            return flow;
        }
        return flow.onErrorResume(e -> {
            log.error("Error al enviar comprobante {} al SRI: {}", comprobante.getId(), e.getMessage());
            comprobantesErroresSri.increment();
            EnvioSri envioError = EnvioSri.builder()
                    .idComprobante(comprobante.getId())
                    .idCompania(comprobante.getIdCompania())
                    .idSucursal(comprobante.getIdSucursal())
                    .tipoOperacion("RECEPCION")
                    .endpointUrl(endpointUrl)
                    .exitoso(false)
                    .estadoSri("ERROR")
                    .mensajeError(e.getMessage())
                    .intentoNumero((short) (cola.getIntentos() != null ? cola.getIntentos() + 1 : 1))
                    .build();
            return envioSriRepository.save(envioError)
                    .then(scheduleRetry(cola, List.of(e.getMessage() != null ? e.getMessage() : "Error desconocido"),
                            false, false))
                    .then(comprobanteRepository.updateEstado(
                            comprobante.getId(), "ERROR", null, null, null, null, null));
        });
    }

    private Mono<Comprobante> autorizarComprobante(Comprobante comprobante, ColaEnvio cola, String ambiente,
                                                    String xmlFirmado, boolean encolarSoloEnFallo) {
        String endpointUrl = resolveAutorizacionEndpoint(ambiente);

        Mono<Comprobante> flow = sriSoapPort.autorizarComprobante(comprobante.getClaveAcceso(), ambiente)
                .flatMap(respuestaAutorizacion -> {
                    boolean exitoso = "AUTORIZADO".equals(respuestaAutorizacion.getEstado());
                    String mensajeError = respuestaAutorizacion.getMensajes() != null
                            ? String.join("; ", respuestaAutorizacion.getMensajes()) : null;

                    EnvioSri envio = EnvioSri.builder()
                            .idComprobante(comprobante.getId())
                            .idCompania(comprobante.getIdCompania())
                            .idSucursal(comprobante.getIdSucursal())
                            .tipoOperacion("AUTORIZACION")
                            .endpointUrl(endpointUrl)
                            .exitoso(exitoso)
                            .estadoSri(respuestaAutorizacion.getEstado())
                            .mensajeError(exitoso ? null : mensajeError)
                            .intentoNumero((short) (cola.getIntentos() != null ? cola.getIntentos() + 1 : 1))
                            .build();

                    if (exitoso) {
                        comprobantesAutorizados.increment();
                        // Si veníamos por la ruta síncrona (encolarSoloEnFallo=true) NO
                        // creamos la fila en cola_envio (path feliz sin cola). Si veníamos
                        // por la ruta async, la fila ya existe y debemos cerrarla.
                        Mono<?> cerrarCola = encolarSoloEnFallo
                                ? Mono.empty()
                                : markColaCompletada(cola);
                        return envioSriRepository.save(envio)
                                .then(cerrarCola)
                                .then(comprobanteRepository.updateEstado(
                                        comprobante.getId(),
                                        "AUTORIZADO",
                                        null,
                                        null,
                                        null,
                                        respuestaAutorizacion.getFechaAutorizacion(),
                                        respuestaAutorizacion.getNumeroAutorizacion()
                                ))
                                .flatMap(this::generarYDistribuirRide);
                    } else {
                        comprobantesErroresSri.increment();
                        return envioSriRepository.save(envio)
                                .then(scheduleRetry(cola, respuestaAutorizacion.getMensajes(), encolarSoloEnFallo, false))
                                .then(comprobanteRepository.updateEstado(
                                        comprobante.getId(), "NO_AUTORIZADO", null, null, null, null, null));
                    }
                });

        if (encolarSoloEnFallo) {
            return flow;
        }
        return flow.onErrorResume(e -> {
            log.error("Error al autorizar comprobante {}: {}", comprobante.getId(), e.getMessage());
            comprobantesErroresSri.increment();
            EnvioSri envioError = EnvioSri.builder()
                    .idComprobante(comprobante.getId())
                    .idCompania(comprobante.getIdCompania())
                    .idSucursal(comprobante.getIdSucursal())
                    .tipoOperacion("AUTORIZACION")
                    .endpointUrl(endpointUrl)
                    .exitoso(false)
                    .estadoSri("ERROR")
                    .mensajeError(e.getMessage())
                    .intentoNumero((short) (cola.getIntentos() != null ? cola.getIntentos() + 1 : 1))
                    .build();
            return envioSriRepository.save(envioError)
                    .then(scheduleRetry(cola, List.of(e.getMessage() != null ? e.getMessage() : "Error desconocido"),
                            false, false))
                    .then(comprobanteRepository.updateEstado(
                            comprobante.getId(), "ERROR", null, null, null, null, null));
        });
    }

    private Mono<Comprobante> generarYDistribuirRide(Comprobante comprobante) {
        Mono<List<ComprobanteDetalle>> detallesMono = comprobanteRepository
                .findDetallesByIdComprobante(comprobante.getId())
                .collectList()
                .defaultIfEmpty(List.of());

        Mono<ConfigSri> configMono = configSriRepository
                .findByEmpresa(comprobante.getIdCompania(), comprobante.getIdSucursal())
                .onErrorResume(e -> {
                    log.warn("No se pudo obtener ConfigSri para comprobante {}: {}", comprobante.getId(), e.getMessage());
                    return Mono.empty();
                });

        return Mono.zip(detallesMono, configMono.switchIfEmpty(Mono.just(ConfigSri.builder().build())))
                .flatMap(tuple -> {
                    List<ComprobanteDetalle> detalles = tuple.getT1();
                    ConfigSri configSri = tuple.getT2();
                    return ridePdfPort.generarRide(comprobante, detalles, configSri);
                })
                .flatMap(ridePdf -> fileStoragePort.saveRidePdf(comprobante.getId(), ridePdf)
                        .flatMap(ridePdfPath -> comprobanteRepository.updateEstado(
                                comprobante.getId(), comprobante.getEstado(),
                                comprobante.getXmlFirmadoPath(),
                                comprobante.getXmlAutorizadoPath(),
                                ridePdfPath,
                                comprobante.getFechaAutorizacion(),
                                comprobante.getNumeroAutorizacion()
                        ))
                        .doOnNext(updated -> emailNotificationPort.enviarFactura(updated, ridePdf)
                                .onErrorResume(e -> {
                                    log.warn("Error al enviar email para comprobante {}: {}", updated.getId(), e.getMessage());
                                    return Mono.empty();
                                })
                                .subscribe())
                )
                .onErrorResume(e -> {
                    log.error("Error en generación de RIDE para comprobante {}: {}", comprobante.getId(), e.getMessage());
                    return Mono.just(comprobante);
                });
    }

    /**
     * Programa el reintento con backoff.
     *
     * @param cola                 fila (real o virtual) que trae el conteo de intentos.
     * @param mensajes             errores a persistir.
     * @param encolarSoloEnFallo   si {@code true}, la fila aún no existe en BD:
     *                             hay que hacer {@code save} en vez de {@code update}.
     * @param inmediato            si {@code true}, {@code proxima_ejecucion = now()}
     *                             (solo aplica al path síncrono cuando cae por
     *                             timeout/error de red).
     */
    private Mono<ColaEnvio> scheduleRetry(ColaEnvio cola, List<String> mensajes,
                                           boolean encolarSoloEnFallo, boolean inmediato) {
        short nuevoIntento = (short) ((cola.getIntentos() != null ? cola.getIntentos() : 0) + 1);
        short maxIntentos = cola.getMaxIntentos() != null ? cola.getMaxIntentos() : 5;
        String nuevoEstado = nuevoIntento >= maxIntentos ? "FALLIDO_DEFINITIVO" : "PENDIENTE";
        OffsetDateTime proximaEjecucion;
        if (inmediato) {
            proximaEjecucion = OffsetDateTime.now();
        } else {
            int delayIndex = Math.min(nuevoIntento - 1, RETRY_DELAYS_MINUTES.length - 1);
            long delayMinutes = RETRY_DELAYS_MINUTES[delayIndex];
            proximaEjecucion = OffsetDateTime.now().plusMinutes(delayMinutes);
        }

        comprobantesReintentos.increment();

        ColaEnvio updated = cola.toBuilder()
                .intentos(nuevoIntento)
                .estado(nuevoEstado)
                .proximaEjecucion(proximaEjecucion)
                .ultimoError(mensajes != null ? String.join("; ", mensajes) : null)
                .build();
        return encolarSoloEnFallo
                ? colaEnvioRepository.save(updated)
                : colaEnvioRepository.update(updated);
    }

    private Mono<ColaEnvio> markColaCompletada(ColaEnvio cola) {
        ColaEnvio updated = cola.toBuilder()
                .estado("COMPLETADO")
                .build();
        return colaEnvioRepository.update(updated);
    }

    /**
     * G2 · Path timeout. Al vencer el timeout del pipeline síncrono:
     * <ol>
     *     <li>Marca el comprobante en estado {@code ERROR}.</li>
     *     <li>Persiste una fila en {@code cola_envio} con
     *         {@code proxima_ejecucion = now()} para que el scheduler la
     *         procese en su próxima pasada (dentro de 60 s).</li>
     *     <li>Incrementa {@code sri.emision.timeouts}.</li>
     * </ol>
     */
    private Mono<Comprobante> handleTimeoutInmediato(Comprobante comprobante, Throwable e, long startNanos) {
        log.warn("Timeout en emisión inmediata SRI para comprobante {} (>{}s): {}",
                comprobante.getId(), sriTimeoutProperties.getEnvioSeconds(), e.getMessage());
        sriEmisionTimeouts.increment();
        sriEmisionDuracion.record(Duration.ofNanos(System.nanoTime() - startNanos));
        return persistirFalloInmediato(
                comprobante,
                "Timeout de " + sriTimeoutProperties.getEnvioSeconds() + "s en el pipeline síncrono de emisión al SRI",
                true);
    }

    private Mono<Comprobante> handleErrorInmediato(Comprobante comprobante, Throwable e, long startNanos) {
        log.error("Error en emisión inmediata SRI para comprobante {}: {}", comprobante.getId(), e.getMessage());
        sriEmisionDuracion.record(Duration.ofNanos(System.nanoTime() - startNanos));
        return persistirFalloInmediato(comprobante, e.getMessage(), true);
    }

    private Mono<Comprobante> persistirFalloInmediato(Comprobante comprobante, String mensajeError, boolean inmediato) {
        comprobantesErroresSri.increment();
        ColaEnvio virtual = ColaEnvio.builder()
                .idComprobante(comprobante.getId())
                .idCompania(comprobante.getIdCompania())
                .idSucursal(comprobante.getIdSucursal())
                .estado("PENDIENTE")
                .intentos((short) 0)
                .maxIntentos((short) 5)
                .proximaEjecucion(OffsetDateTime.now())
                .build();
        return scheduleRetry(virtual, List.of(mensajeError != null ? mensajeError : "Error desconocido"), true, inmediato)
                .then(comprobanteRepository.updateEstado(
                        comprobante.getId(), "ERROR", null, null, null, null, null));
    }

    /**
     * G2 · Construcción del XML real cuando venimos por la ruta síncrona
     * (path de {@code POST /facturas}): los detalles, pagos y configSri
     * ya están en memoria — evitamos el round-trip a BD.
     * <p>
     * TODO(G6-follow): {@code codigoTarifaIva} por detalle está hardcodeado
     * a IVA 15% (código {@code "2"}, porcentaje {@code "4"}, tarifa {@code 15.00}).
     * Coherente con el hardcode actual del builder para v2.24.
     */
    private String buildXmlInmediato(Comprobante comprobante, List<ComprobanteDetalle> detalles,
                                     List<FacturaXmlBuilder.Pago> pagos, ConfigSri configSri) {
        List<FacturaXmlBuilder.ImpuestoTotal> impuestosTotales = buildImpuestosTotales(detalles);
        return facturaXmlBuilder.buildXml(comprobante, detalles, configSri, impuestosTotales, pagos);
    }

    /**
     * G2 · Path async ({@code /enviar} manual y scheduler): reconstruye
     * detalles, pagos y ConfigSri desde BD para poder generar el XML real.
     * <p>
     * TODO(G6-follow): cuando exista la tabla {@code facturacion.comprobante_pagos}
     * poblada por {@link ComprobanteService#emitirFactura}, leer los pagos
     * reales en vez de sintetizar uno con {@code formaPago="01"} y
     * {@code total=comprobante.total}.
     */
    private Mono<String> buildXmlDesdeBD(Comprobante comprobante) {
        Mono<List<ComprobanteDetalle>> detallesMono = comprobanteRepository
                .findDetallesByIdComprobante(comprobante.getId())
                .collectList()
                .defaultIfEmpty(List.of());

        Mono<ConfigSri> configMono = configSriRepository
                .findByEmpresa(comprobante.getIdCompania(), comprobante.getIdSucursal())
                .switchIfEmpty(Mono.just(ConfigSri.builder().build()));

        return Mono.zip(detallesMono, configMono)
                .map(tuple -> {
                    List<ComprobanteDetalle> detalles = tuple.getT1();
                    ConfigSri configSri = tuple.getT2();
                    // TODO(G6-follow): reemplazar por lectura real de facturacion.comprobante_pagos
                    // cuando ComprobanteService.emitirFactura persista los pagos del command.
                    List<FacturaXmlBuilder.Pago> pagos = List.of(new FacturaXmlBuilder.Pago(
                            "01",
                            comprobante.getTotal() != null ? comprobante.getTotal() : BigDecimal.ZERO
                    ));
                    List<FacturaXmlBuilder.ImpuestoTotal> impuestosTotales = buildImpuestosTotales(detalles);
                    return facturaXmlBuilder.buildXml(comprobante, detalles, configSri, impuestosTotales, pagos);
                });
    }

    /**
     * Agrega el IVA por detalle en un único registro {@code totalImpuesto}
     * (código {@code "2"}, porcentaje {@code "4"}, tarifa 15%). Coherente con
     * el hardcode del builder por línea (v2.24 IVA 15%).
     * <p>
     * TODO(G6-follow): consultar el catálogo {@code sri.tarifas_iva} por
     * detalle cuando el DTO exponga {@code codigoTarifaIva}.
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

    /**
     * Selecciona el contador de emisiones correcto según el tipo de comprobante.
     * Tipo {@code "04"} → NC; cualquier otro (por ahora solo {@code "01"}) →
     * factura. G4 · agregado con notas de crédito.
     */
    private void incrementarContadorEmision(Comprobante comprobante) {
        if ("04".equals(comprobante.getTipoComprobante())) {
            comprobantesEmitidosNotaCredito.increment();
        } else {
            comprobantesEmitidosFactura.increment();
        }
    }

    private String resolveRecepcionEndpoint(String ambiente) {
        return "2".equals(ambiente)
                ? "https://cel.sri.gob.ec/comprobantes-electronicos-ws/RecepcionComprobantesOffline"
                : "https://celcer.sri.gob.ec/comprobantes-electronicos-ws/RecepcionComprobantesOffline";
    }

    private String resolveAutorizacionEndpoint(String ambiente) {
        return "2".equals(ambiente)
                ? "https://cel.sri.gob.ec/comprobantes-electronicos-ws/AutorizacionComprobantesOffline"
                : "https://celcer.sri.gob.ec/comprobantes-electronicos-ws/AutorizacionComprobantesOffline?wsdl";
    }
}
