package com.gymadmin.billing.infrastructure.adapter.out.soap;

import com.gymadmin.billing.domain.model.sri.RespuestaAutorizacion;
import com.gymadmin.billing.domain.model.sri.RespuestaRecepcion;
import com.gymadmin.billing.domain.port.out.SriSoapPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class SriSoapAdapter implements SriSoapPort {

    private static final String RECEPCION_SOAP_ACTION = "validarComprobante";
    private static final String AUTORIZACION_SOAP_ACTION = "autorizacionComprobante";

    private static final DateTimeFormatter SRI_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss[.SSS][XXX][X]");

    private final WebClient sriWebClient;
    private final String wsdlRecepcion;
    private final String wsdlAutorizacion;

    public SriSoapAdapter(
            WebClient sriWebClient,
            @Value("${sri.wsdl.recepcion}") String wsdlRecepcion,
            @Value("${sri.wsdl.autorizacion}") String wsdlAutorizacion) {
        this.sriWebClient = sriWebClient;
        this.wsdlRecepcion = stripWsdl(wsdlRecepcion);
        this.wsdlAutorizacion = stripWsdl(wsdlAutorizacion);
    }

    @Override
    public Mono<RespuestaRecepcion> enviarComprobante(String xmlBase64, String ambiente) {
        String endpointUrl = resolveEndpoint(wsdlRecepcion, ambiente);
        String soapEnvelope = buildRecepcionEnvelope(xmlBase64);

        return sriWebClient.post()
                .uri(endpointUrl)
                .contentType(MediaType.valueOf("text/xml; charset=UTF-8"))
                .header("SOAPAction", RECEPCION_SOAP_ACTION)
                .bodyValue(soapEnvelope)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(responseBody -> Mono.fromCallable(() -> parseRecepcionResponse(responseBody))
                        .subscribeOn(Schedulers.boundedElastic()))
                .onErrorResume(e -> {
                    log.error("Error al llamar servicio de recepción SRI: {}", e.getMessage());
                    return Mono.just(RespuestaRecepcion.builder()
                            .estado("DEVUELTA")
                            .mensajes(List.of("Error de comunicación con SRI: " + e.getMessage()))
                            .build());
                });
    }

    @Override
    public Mono<RespuestaAutorizacion> autorizarComprobante(String claveAcceso, String ambiente) {
        String endpointUrl = resolveEndpoint(wsdlAutorizacion, ambiente);
        String soapEnvelope = buildAutorizacionEnvelope(claveAcceso);

        return sriWebClient.post()
                .uri(endpointUrl)
                .contentType(MediaType.valueOf("text/xml; charset=UTF-8"))
                .header("SOAPAction", AUTORIZACION_SOAP_ACTION)
                .bodyValue(soapEnvelope)
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(responseBody -> Mono.fromCallable(() -> parseAutorizacionResponse(responseBody))
                        .subscribeOn(Schedulers.boundedElastic()))
                .onErrorResume(e -> {
                    log.error("Error al llamar servicio de autorización SRI: {}", e.getMessage());
                    return Mono.just(RespuestaAutorizacion.builder()
                            .estado("NO AUTORIZADO")
                            .mensajes(List.of("Error de comunicación con SRI: " + e.getMessage()))
                            .build());
                });
    }

    private String buildRecepcionEnvelope(String xmlBase64) {
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ec="http://ec.gob.sri.ws.recepcion">
                  <soapenv:Header/>
                  <soapenv:Body>
                    <ec:validarComprobante>
                      <xml>%s</xml>
                    </ec:validarComprobante>
                  </soapenv:Body>
                </soapenv:Envelope>""".formatted(xmlBase64);
    }

    private String buildAutorizacionEnvelope(String claveAcceso) {
        return """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:ec="http://ec.gob.sri.ws.autorizacion">
                  <soapenv:Header/>
                  <soapenv:Body>
                    <ec:autorizacionComprobante>
                      <claveAccesoComprobante>%s</claveAccesoComprobante>
                    </ec:autorizacionComprobante>
                  </soapenv:Body>
                </soapenv:Envelope>""".formatted(claveAcceso);
    }

    private RespuestaRecepcion parseRecepcionResponse(String responseXml) throws Exception {
        Document doc = parseXml(responseXml);

        String estado = extractText(doc, "estado");
        List<String> mensajes = extractMensajes(doc);

        return RespuestaRecepcion.builder()
                .estado(estado != null ? estado : "DEVUELTA")
                .mensajes(mensajes)
                .build();
    }

    private RespuestaAutorizacion parseAutorizacionResponse(String responseXml) throws Exception {
        Document doc = parseXml(responseXml);

        NodeList autorizacionNodes = doc.getElementsByTagName("autorizacion");
        if (autorizacionNodes.getLength() == 0) {
            return RespuestaAutorizacion.builder()
                    .estado("NO AUTORIZADO")
                    .mensajes(List.of("Respuesta vacía del SRI"))
                    .build();
        }

        Element autorizacion = (Element) autorizacionNodes.item(0);
        String estado = extractTextFromElement(autorizacion, "estado");
        String numeroAutorizacion = extractTextFromElement(autorizacion, "numeroAutorizacion");
        String fechaStr = extractTextFromElement(autorizacion, "fechaAutorizacion");
        String xmlAutorizado = extractTextFromElement(autorizacion, "comprobante");
        List<String> mensajes = extractMensajesFromElement(autorizacion);

        OffsetDateTime fechaAutorizacion = null;
        if (fechaStr != null && !fechaStr.isBlank()) {
            try {
                fechaAutorizacion = OffsetDateTime.parse(fechaStr, SRI_DATE_FORMATTER);
            } catch (Exception e) {
                log.warn("No se pudo parsear fecha de autorización SRI: {}", fechaStr);
            }
        }

        return RespuestaAutorizacion.builder()
                .estado(estado != null ? estado : "NO AUTORIZADO")
                .numeroAutorizacion(numeroAutorizacion)
                .fechaAutorizacion(fechaAutorizacion)
                .xmlAutorizado(xmlAutorizado)
                .mensajes(mensajes)
                .build();
    }

    private Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        return db.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private String extractText(Document doc, String tagName) {
        NodeList nodes = doc.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return null;
    }

    private String extractTextFromElement(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return null;
    }

    private List<String> extractMensajes(Document doc) {
        List<String> mensajes = new ArrayList<>();
        NodeList mensajeNodes = doc.getElementsByTagName("mensaje");
        for (int i = 0; i < mensajeNodes.getLength(); i++) {
            Element mensaje = (Element) mensajeNodes.item(i);
            String identificador = extractTextFromElement(mensaje, "identificador");
            String info = extractTextFromElement(mensaje, "mensaje");
            if (info != null) {
                mensajes.add(identificador != null ? identificador + ": " + info : info);
            }
        }
        return mensajes;
    }

    private List<String> extractMensajesFromElement(Element parent) {
        List<String> mensajes = new ArrayList<>();
        NodeList mensajeNodes = parent.getElementsByTagName("mensaje");
        for (int i = 0; i < mensajeNodes.getLength(); i++) {
            Element mensaje = (Element) mensajeNodes.item(i);
            String identificador = extractTextFromElement(mensaje, "identificador");
            String info = extractTextFromElement(mensaje, "mensaje");
            if (info != null) {
                mensajes.add(identificador != null ? identificador + ": " + info : info);
            }
        }
        return mensajes;
    }

    private String resolveEndpoint(String baseEndpoint, String ambiente) {
        // ambiente "1" = pruebas, "2" = produccion
        // The endpoint is already resolved from WSDL URL; the WSDL itself encodes ambiente
        return baseEndpoint;
    }

    private String stripWsdl(String url) {
        if (url != null && url.endsWith("?wsdl")) {
            return url.substring(0, url.length() - 5);
        }
        return url;
    }
}
