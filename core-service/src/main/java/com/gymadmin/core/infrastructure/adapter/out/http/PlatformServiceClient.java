package com.gymadmin.core.infrastructure.adapter.out.http;

import com.gymadmin.core.domain.exception.LimiteAlcanzadoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * REQ-SAAS-001 (RN-06, Sub-fase 1.4): cliente HTTP hacia platform-service para
 * verificar cuotas de plan antes de crear un recurso en core-service (por
 * ejemplo, un cliente).
 * <p>
 * Autenticación inter-service: header {@code X-Internal-Call} con secreto
 * compartido.
 */
@Component
public class PlatformServiceClient {

    public static final String HEADER_INTERNAL_CALL = "X-Internal-Call";

    private static final Logger log = LoggerFactory.getLogger(PlatformServiceClient.class);

    private final WebClient webClient;
    private final String internalSecret;

    public PlatformServiceClient(WebClient.Builder builder,
                                  @Value("${services.platform.url:${PLATFORM_SERVICE_URL:http://localhost:8081}}") String baseUrl,
                                  @Value("${services.internal.secret:${INTERNAL_SECRET:platform-secret-dev}}") String internalSecret) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.internalSecret = internalSecret;
    }

    /**
     * REQ-SAAS-001: consulta si el tenant {@code idCompania} puede crear un
     * recurso adicional de tipo {@code recurso}. Ante error o timeout retorna
     * {@code true} (fail-open) para no bloquear operaciones si platform-service
     * está caído — la validación real ocurre en el propio platform-service.
     */
    public Mono<Boolean> checkLimite(Long idCompania, String recurso) {
        return webClient.get()
                .uri("/internal/v1/companias/{id}/uso-limites/{recurso}", idCompania, recurso)
                .header(HEADER_INTERNAL_CALL, internalSecret)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(3))
                .map(body -> Boolean.TRUE.equals(body.get("permite")))
                .onErrorResume(err -> {
                    log.warn("PlatformServiceClient.checkLimite({},{}) falló: {} — fail-open",
                            idCompania, recurso, err.getMessage());
                    return Mono.just(true);
                });
    }

    /**
     * Variante que emite {@link LimiteAlcanzadoException} en lugar de un boolean.
     * Envía también los campos {@code actual}, {@code maximo} y {@code planCodigo}
     * del response del platform-service para propagar el detalle al cliente HTTP.
     */
    public Mono<Void> requireLimite(Long idCompania, String recurso) {
        return webClient.get()
                .uri("/internal/v1/companias/{id}/uso-limites/{recurso}", idCompania, recurso)
                .header(HEADER_INTERNAL_CALL, internalSecret)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(3))
                .flatMap(body -> {
                    boolean permite = Boolean.TRUE.equals(body.get("permite"));
                    if (permite) {
                        return Mono.empty();
                    }
                    long actual = toLong(body.get("actual"));
                    long maximo = toLong(body.get("maximo"));
                    String plan = body.get("planCodigo") != null ? body.get("planCodigo").toString() : null;
                    return Mono.<Void>error(new LimiteAlcanzadoException(recurso, actual, maximo, plan));
                })
                .onErrorResume(err -> {
                    if (err instanceof LimiteAlcanzadoException) {
                        return Mono.error(err);
                    }
                    log.warn("PlatformServiceClient.requireLimite({},{}) falló: {} — fail-open",
                            idCompania, recurso, err.getMessage());
                    return Mono.empty();
                });
    }

    private long toLong(Object o) {
        if (o instanceof Number n) return n.longValue();
        return 0L;
    }
}
