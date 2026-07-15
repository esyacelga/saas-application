package com.gymadmin.platform.infrastructure.adapter.out.whatsapp;

import com.gymadmin.platform.domain.exception.WhatsAppSendException;
import com.gymadmin.platform.domain.port.out.WhatsAppSender;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Adaptador de salida para Meta WhatsApp Cloud API — implementa {@link WhatsAppSender}
 * enviando plantillas HSM vía {@code POST /{phone-number-id}/messages}.
 *
 * <p>Molde: {@code EmailAdapter}. Si faltan {@code META_ACCESS_TOKEN} o
 * {@code META_PHONE_NUMBER_ID}, loguea WARN y devuelve {@link Mono#empty()} — el servicio arranca
 * y los tests corren sin credenciales (dev/CI).
 *
 * <p><b>R5 — timeouts y clasificación de errores:</b> {@link WebClient} sobre un
 * {@link HttpClient} con timeout de conexión (5s) y lectura (10s). Distingue errores retryables
 * (429, 5xx, timeout) de no-retryables (4xx de negocio con {@code error.code}) mapeándolos a
 * {@link WhatsAppSendException}.
 */
@Component
public class MetaWhatsAppAdapter implements WhatsAppSender {

    private static final Logger log = LoggerFactory.getLogger(MetaWhatsAppAdapter.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final WebClient webClient;
    private final String phoneNumberId;
    private final String accessToken;
    private final boolean configurado;

    public MetaWhatsAppAdapter(
            WebClient.Builder builder,
            @Value("${whatsapp.meta.api-base-url:${META_API_BASE_URL:https://graph.facebook.com}}") String apiBaseUrl,
            @Value("${whatsapp.meta.api-version:${META_API_VERSION:v21.0}}") String apiVersion,
            @Value("${whatsapp.meta.phone-number-id:${META_PHONE_NUMBER_ID:}}") String phoneNumberId,
            @Value("${whatsapp.meta.access-token:${META_ACCESS_TOKEN:}}") String accessToken) {

        this.phoneNumberId = phoneNumberId;
        this.accessToken = accessToken;
        this.configurado = phoneNumberId != null && !phoneNumberId.isBlank()
                && accessToken != null && !accessToken.isBlank();

        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECT_TIMEOUT.toMillis())
                .doOnConnected(conn -> conn.addHandlerLast(
                        new ReadTimeoutHandler(READ_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)));

        this.webClient = builder
                .baseUrl(apiBaseUrl + "/" + apiVersion)
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Override
    public Mono<Void> enviarPlantilla(String destinatarioE164, String templateName, String idioma, List<String> params) {
        if (!configurado) {
            log.warn("Meta WhatsApp no configurado (META_PHONE_NUMBER_ID/META_ACCESS_TOKEN vacíos) — "
                    + "se omite envío de '{}' a {}", templateName, destinatarioE164);
            return Mono.empty();
        }

        Map<String, Object> payload = construirPayload(destinatarioE164, templateName, idioma, params);

        return webClient.post()
                .uri("/{phoneNumberId}/messages", phoneNumberId)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(Map.class)
                                .defaultIfEmpty(Map.of())
                                .flatMap(body -> Mono.error(mapearError(resp.statusCode(), body))))
                .bodyToMono(Void.class)
                .onErrorMap(this::esErrorDeTransporte, e -> new WhatsAppSendException(
                        "Fallo de transporte al llamar a Meta: " + e.getMessage(), true, null, e));
    }

    /** Payload Meta {@code type=template} con el body y sus placeholders en orden. */
    private Map<String, Object> construirPayload(String to, String templateName, String idioma, List<String> params) {
        List<Map<String, String>> parameters = new ArrayList<>();
        if (params != null) {
            for (String p : params) {
                parameters.add(Map.of("type", "text", "text", p != null ? p : ""));
            }
        }

        List<Map<String, Object>> components = List.of(Map.of(
                "type", "body",
                "parameters", parameters));

        Map<String, Object> template = Map.of(
                "name", templateName,
                "language", Map.of("code", idioma),
                "components", components);

        // Meta exige el número SIN el '+' inicial en el campo "to".
        String toSinMas = to.startsWith("+") ? to.substring(1) : to;

        return Map.of(
                "messaging_product", "whatsapp",
                "to", toSinMas,
                "type", "template",
                "template", template);
    }

    /**
     * Mapea la respuesta de error de Meta a {@link WhatsAppSendException}, clasificando
     * retryable (429, 5xx) vs no-retryable (resto de 4xx de negocio).
     */
    private WhatsAppSendException mapearError(HttpStatusCode status, Map<?, ?> body) {
        Integer metaCode = extraerErrorCode(body);
        String detalle = extraerErrorMessage(body);
        boolean retryable = status.value() == 429 || status.is5xxServerError();
        String msg = String.format("Meta respondió %d%s: %s",
                status.value(),
                metaCode != null ? " (code=" + metaCode + ")" : "",
                detalle);
        return new WhatsAppSendException(msg, retryable, metaCode);
    }

    private boolean esErrorDeTransporte(Throwable t) {
        // Ya clasificados (respuesta HTTP de error) no se re-mapean.
        return !(t instanceof WhatsAppSendException);
    }

    private Integer extraerErrorCode(Map<?, ?> body) {
        Object error = body.get("error");
        if (error instanceof Map<?, ?> em && em.get("code") instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    private String extraerErrorMessage(Map<?, ?> body) {
        Object error = body.get("error");
        if (error instanceof Map<?, ?> em && em.get("message") != null) {
            return String.valueOf(em.get("message"));
        }
        return "sin detalle";
    }
}
