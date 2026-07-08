package com.gymadmin.auth.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class CorsGlobalFilter implements WebFilter {

    private final AppProperties appProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String origin = exchange.getRequest().getHeaders().getFirst(HttpHeaders.ORIGIN);

        AppProperties.Cors cors = appProperties.getCors();
        boolean originAllowed = origin != null &&
                (cors.isAllowAll() || cors.getAllowedOrigins().contains(origin));

        if (originAllowed) {
            HttpHeaders headers = exchange.getResponse().getHeaders();
            headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
            headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
            headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PUT,DELETE,OPTIONS");
            headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, "Content-Type,Authorization");
            headers.add(HttpHeaders.VARY, HttpHeaders.ORIGIN);

            if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
                exchange.getResponse().setStatusCode(HttpStatus.OK);
                return exchange.getResponse().setComplete();
            }
        }

        return chain.filter(exchange);
    }
}
