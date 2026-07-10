package com.gymadmin.platform.infrastructure.adapter.out.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * REQ-SAAS-001 (RN-06): cliente HTTP hacia {@code core-service} para consultar
 * el conteo de clientes activos de un tenant.
 * <p>
 * Autenticación inter-service: header {@code X-Internal-Call} con un secreto
 * compartido (default {@code platform-secret}).
 */
@Component
public class CoreServiceClient {

    public static final String HEADER_INTERNAL_CALL = "X-Internal-Call";

    private static final Logger log = LoggerFactory.getLogger(CoreServiceClient.class);

    private final WebClient webClient;
    private final String internalSecret;

    public CoreServiceClient(WebClient.Builder builder,
                             @Value("${services.core.url:${CORE_SERVICE_URL:http://localhost:8083}}") String baseUrl,
                             @Value("${services.internal.secret:platform-secret}") String internalSecret) {
        this.webClient = builder.baseUrl(baseUrl).build();
        this.internalSecret = internalSecret;
    }

    /**
     * GET /internal/v1/companias/{id}/clientes-activos/count → devuelve el
     * conteo de clientes activos del tenant. Ante error o timeout retorna 0
     * para no bloquear la creación (el limitador ya usa advisory lock).
     */
    public Mono<Long> contarClientesActivos(Long idCompania) {
        return webClient.get()
                .uri("/internal/v1/companias/{id}/clientes-activos/count", idCompania)
                .header(HEADER_INTERNAL_CALL, internalSecret)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(3))
                .map(body -> {
                    Object count = body.get("count");
                    if (count instanceof Number n) {
                        return n.longValue();
                    }
                    return 0L;
                })
                .onErrorResume(err -> {
                    log.warn("CoreServiceClient.contarClientesActivos({}) falló: {}",
                            idCompania, err.getMessage());
                    return Mono.just(0L);
                });
    }
}
