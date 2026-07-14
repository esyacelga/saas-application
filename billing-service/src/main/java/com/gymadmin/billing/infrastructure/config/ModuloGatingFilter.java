package com.gymadmin.billing.infrastructure.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Component
public class ModuloGatingFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(ModuloGatingFilter.class);

    private static final List<String> PROTECTED_PREFIXES = List.of(
            "/api/v1/comprobantes",
            "/api/v1/notas-credito",
            "/api/v1/anulaciones",
            "/api/v1/reportes"
    );

    private final WebClient platformWebClient;
    private final boolean enabled;
    private final String moduloCodigo;
    private final Cache<Long, Boolean> cache;

    public ModuloGatingFilter(
            WebClient platformWebClient,
            @Value("${billing.gating.enabled:true}") boolean enabled,
            @Value("${billing.gating.modulo-codigo:FACTURACION}") String moduloCodigo,
            @Value("${billing.gating.cache-ttl-seconds:60}") long cacheTtlSeconds,
            @Value("${billing.gating.cache-max-size:10000}") long cacheMaxSize) {
        this.platformWebClient = platformWebClient;
        this.enabled = enabled;
        this.moduloCodigo = moduloCodigo;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(cacheTtlSeconds))
                .maximumSize(cacheMaxSize)
                .build();
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!enabled) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (!isProtected(path)) {
            return chain.filter(exchange);
        }

        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(auth -> auth != null && auth.getPrincipal() instanceof JwtPrincipal)
                .map(auth -> (JwtPrincipal) auth.getPrincipal())
                .flatMap(principal -> evaluate(exchange, chain, principal).thenReturn(Boolean.TRUE))
                .switchIfEmpty(Mono.defer(() -> chain.filter(exchange).thenReturn(Boolean.TRUE)))
                .then();
    }

    private Mono<Void> evaluate(ServerWebExchange exchange, WebFilterChain chain, JwtPrincipal principal) {
        if (principal.isPlataforma() || principal.isSuperAdmin()) {
            return chain.filter(exchange);
        }

        Long idCompania = principal.getIdCompania();
        if (idCompania == null) {
            return denyResponse(exchange, HttpStatus.FORBIDDEN.value(), "id_compania_missing");
        }

        Boolean cached = cache.getIfPresent(idCompania);
        if (cached != null) {
            if (cached) {
                return chain.filter(exchange);
            }
            return denyResponse(exchange, HttpStatus.FORBIDDEN.value(), "modulo_no_incluido");
        }

        return checkPlatform(exchange, chain, idCompania);
    }

    private Mono<Void> checkPlatform(ServerWebExchange exchange, WebFilterChain chain, Long idCompania) {
        return platformWebClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/modulos/check")
                        .queryParam("id_compania", idCompania)
                        .queryParam("codigo", moduloCodigo)
                        .build())
                .exchangeToMono(resp -> {
                    int status = resp.statusCode().value();
                    return resp.releaseBody().thenReturn(status);
                })
                .flatMap(status -> handleStatus(exchange, chain, idCompania, status))
                .onErrorResume(ex -> {
                    log.warn("Modulo gating unavailable for id_compania={} error={}",
                            idCompania, ex.getClass().getSimpleName());
                    return denyResponse(exchange, HttpStatus.SERVICE_UNAVAILABLE.value(), "gate_unavailable");
                });
    }

    private Mono<Void> handleStatus(ServerWebExchange exchange, WebFilterChain chain, Long idCompania, int status) {
        return switch (status) {
            case 200 -> {
                cache.put(idCompania, Boolean.TRUE);
                yield chain.filter(exchange);
            }
            case 403 -> {
                cache.put(idCompania, Boolean.FALSE);
                yield denyResponse(exchange, HttpStatus.FORBIDDEN.value(), "modulo_no_incluido");
            }
            case 402 -> denyResponse(exchange, 402, "plan_vencido_o_suspendido");
            default -> {
                log.warn("Modulo gating unexpected status={} for id_compania={}", status, idCompania);
                yield denyResponse(exchange, HttpStatus.SERVICE_UNAVAILABLE.value(), "gate_unavailable");
            }
        };
    }

    private Mono<Void> denyResponse(ServerWebExchange exchange, int status, String razon) {
        exchange.getResponse().setRawStatusCode(status);
        exchange.getResponse().getHeaders().set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        String body = "{\"permitido\":false,\"razon\":\"" + razon + "\"}";
        DataBufferFactory factory = exchange.getResponse().bufferFactory();
        DataBuffer buffer = factory.wrap(body.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private boolean isProtected(String path) {
        for (String prefix : PROTECTED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
