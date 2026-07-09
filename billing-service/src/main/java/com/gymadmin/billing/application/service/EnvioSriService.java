package com.gymadmin.billing.application.service;

import com.gymadmin.billing.domain.model.ColaEnvio;
import com.gymadmin.billing.domain.model.Comprobante;
import com.gymadmin.billing.domain.model.EnvioSri;
import com.gymadmin.billing.domain.port.out.CertificadoRepository;
import com.gymadmin.billing.domain.port.out.ColaEnvioRepository;
import com.gymadmin.billing.domain.port.out.ComprobanteRepository;
import com.gymadmin.billing.domain.port.out.EnvioSriRepository;
import com.gymadmin.billing.domain.port.out.FileStoragePort;
import com.gymadmin.billing.domain.port.out.SriSoapPort;
import com.gymadmin.billing.domain.port.out.XmlSignaturePort;
import com.gymadmin.billing.infrastructure.adapter.out.xml.FacturaXmlBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuples;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
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

    public Mono<Comprobante> procesarComprobante(Long idComprobante, Integer idCompania, Integer idSucursal) {
        return comprobanteRepository.findById(idComprobante)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Comprobante no encontrado: " + idComprobante)))
                .flatMap(comprobante -> firmarYEnviar(comprobante, idCompania, idSucursal));
    }

    private Mono<Comprobante> firmarYEnviar(Comprobante comprobante, Integer idCompania, Integer idSucursal) {
        return Mono.zip(
                certificadoRepository.getActiveCertificateContent(idCompania, idSucursal),
                certificadoRepository.getActiveCertificatePassword(idCompania, idSucursal)
        )
        .flatMap(tuple -> {
            byte[] certContent = tuple.getT1();
            String certPassword = tuple.getT2();
            String xmlSinFirmar = buildXmlFromComprobante(comprobante);
            return xmlSignaturePort.sign(xmlSinFirmar, certContent, certPassword)
                    .map(xmlFirmado -> Tuples.of(certContent, certPassword, xmlFirmado));
        })
        .flatMap(tuple -> {
            String xmlFirmado = tuple.getT3();
            return comprobanteRepository.updateEstado(
                    comprobante.getId(), "FIRMADO", null, null, null, null, null
            ).map(c -> Tuples.of(xmlFirmado, c));
        })
        .flatMap(tuple -> {
            String xmlFirmado = tuple.getT1();
            Comprobante updated = tuple.getT2();
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
        .flatMap(tuple -> enviarASri(tuple.getT1(), tuple.getT2(), tuple.getT3(), comprobante.getAmbiente()));
    }

    private Mono<Comprobante> enviarASri(String xmlFirmado, Comprobante comprobante, ColaEnvio cola, String ambiente) {
        String xmlBase64 = java.util.Base64.getEncoder()
                .encodeToString(xmlFirmado.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        String endpointUrl = resolveRecepcionEndpoint(ambiente);

        return sriSoapPort.enviarComprobante(xmlBase64, ambiente)
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
                                .flatMap(c -> autorizarComprobante(c, cola, ambiente, xmlFirmado));
                    } else {
                        return envioSriRepository.save(envio)
                                .then(scheduleRetry(cola, respuestaRecepcion.getMensajes()))
                                .then(comprobanteRepository.updateEstado(
                                        comprobante.getId(), "DEVUELTO", null, null, null, null, null));
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error al enviar comprobante {} al SRI: {}", comprobante.getId(), e.getMessage());
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
                            .then(scheduleRetry(cola, List.of(e.getMessage())))
                            .then(comprobanteRepository.updateEstado(
                                    comprobante.getId(), "ERROR", null, null, null, null, null));
                });
    }

    private Mono<Comprobante> autorizarComprobante(Comprobante comprobante, ColaEnvio cola, String ambiente, String xmlFirmado) {
        String endpointUrl = resolveAutorizacionEndpoint(ambiente);

        return sriSoapPort.autorizarComprobante(comprobante.getClaveAcceso(), ambiente)
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
                        return envioSriRepository.save(envio)
                                .then(markColaCompletada(cola))
                                .then(comprobanteRepository.updateEstado(
                                        comprobante.getId(),
                                        "AUTORIZADO",
                                        null,
                                        null,
                                        null,
                                        respuestaAutorizacion.getFechaAutorizacion(),
                                        respuestaAutorizacion.getNumeroAutorizacion()
                                ));
                    } else {
                        return envioSriRepository.save(envio)
                                .then(scheduleRetry(cola, respuestaAutorizacion.getMensajes()))
                                .then(comprobanteRepository.updateEstado(
                                        comprobante.getId(), "NO_AUTORIZADO", null, null, null, null, null));
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error al autorizar comprobante {}: {}", comprobante.getId(), e.getMessage());
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
                            .then(scheduleRetry(cola, List.of(e.getMessage())))
                            .then(comprobanteRepository.updateEstado(
                                    comprobante.getId(), "ERROR", null, null, null, null, null));
                });
    }

    private Mono<ColaEnvio> scheduleRetry(ColaEnvio cola, List<String> mensajes) {
        short nuevoIntento = (short) ((cola.getIntentos() != null ? cola.getIntentos() : 0) + 1);
        short maxIntentos = cola.getMaxIntentos() != null ? cola.getMaxIntentos() : 5;
        String nuevoEstado = nuevoIntento >= maxIntentos ? "FALLIDO_DEFINITIVO" : "PENDIENTE";
        int delayIndex = Math.min(nuevoIntento - 1, RETRY_DELAYS_MINUTES.length - 1);
        long delayMinutes = RETRY_DELAYS_MINUTES[delayIndex];

        ColaEnvio updated = cola.toBuilder()
                .intentos(nuevoIntento)
                .estado(nuevoEstado)
                .proximaEjecucion(OffsetDateTime.now().plusMinutes(delayMinutes))
                .ultimoError(mensajes != null ? String.join("; ", mensajes) : null)
                .build();
        return colaEnvioRepository.update(updated);
    }

    private Mono<ColaEnvio> markColaCompletada(ColaEnvio cola) {
        ColaEnvio updated = cola.toBuilder()
                .estado("COMPLETADO")
                .build();
        return colaEnvioRepository.update(updated);
    }

    private String buildXmlFromComprobante(Comprobante comprobante) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><factura id=\"comprobante\" version=\"2.1.0\">"
                + "<placeholder claveAcceso=\"" + comprobante.getClaveAcceso() + "\"/></factura>";
    }

    private String resolveRecepcionEndpoint(String ambiente) {
        return "2".equals(ambiente)
                ? "https://cel.sri.gob.ec/comprobantes-electronicos-ws/RecepcionComprobantesOffline"
                : "https://celcer.sri.gob.ec/comprobantes-electronicos-ws/RecepcionComprobantesOffline";
    }

    private String resolveAutorizacionEndpoint(String ambiente) {
        return "2".equals(ambiente)
                ? "https://cel.sri.gob.ec/comprobantes-electronicos-ws/AutorizacionComprobantesOffline"
                : "https://celcer.sri.gob.ec/comprobantes-electronicos-ws/AutorizacionComprobantesOffline";
    }
}
