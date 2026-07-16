package com.gymadmin.attendance.infrastructure.adapter.out.platform;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Fase 6 (R1): consume el endpoint interno de platform-service
 * {@code GET /internal/v1/notif-buckets/socio} para leer el bucket global de aviso previo del socio,
 * en vez de tener el valor hardcodeado en el {@code MensajeriaJob}. Usa el header {@code X-Internal-Call}
 * (no JWT), mismo patrón que {@code CoreServiceClient}.
 *
 * <p>Es tolerante a fallos: si platform no responde, la fila no existe, o el bucket está
 * {@code activo=false}, devuelve un valor seguro para que el job nunca se rompa
 * (ver {@link #obtenerBucketPrevioSocio(int)}).
 */
@Slf4j
@Component
public class PlatformServiceClient {

    /** Header del contrato interno service-to-service (no JWT). Mismo nombre en core/platform. */
    public static final String HEADER_INTERNAL_CALL = "X-Internal-Call";

    /** Bucket que representa "aviso previo desactivado": solo el día 0 aplica. */
    public static final int BUCKET_PREVIO_DESACTIVADO = 0;

    private final WebClient platformWebClient;

    @Value("${services.platform-service.internal-secret:${INTERNAL_SECRET:platform-secret-dev}}")
    private String internalSecret;

    public PlatformServiceClient(WebClient platformWebClient) {
        this.platformWebClient = platformWebClient;
    }

    /**
     * Devuelve los días de aviso previo efectivos del socio:
     * <ul>
     *   <li>bucket {@code activo=true} → {@code dias_previo} de la tabla;</li>
     *   <li>bucket {@code activo=false} → {@link #BUCKET_PREVIO_DESACTIVADO} (0, solo día 0);</li>
     *   <li>platform inaccesible / fila ausente / error → {@code fallback} (el default del job).</li>
     * </ul>
     */
    public Mono<Integer> obtenerBucketPrevioSocio(int fallback) {
        return platformWebClient.get()
                .uri("/internal/v1/notif-buckets/socio")
                .header(HEADER_INTERNAL_CALL, internalSecret)
                .retrieve()
                .bodyToMono(NotifBucketResponse.class)
                .map(r -> r.isActivo() ? r.getDiasPrevio() : BUCKET_PREVIO_DESACTIVADO)
                .doOnNext(v -> log.debug("[PlatformService] bucket previo socio = {}", v))
                .defaultIfEmpty(fallback)
                .onErrorResume(e -> {
                    log.warn("[PlatformService] no se pudo leer bucket previo socio, uso fallback={} causa='{}'",
                            fallback, e.getMessage());
                    return Mono.just(fallback);
                });
    }

    @Getter
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class NotifBucketResponse {
        private String destinatario;
        private Integer diasPrevio;
        private boolean activo;
    }
}
