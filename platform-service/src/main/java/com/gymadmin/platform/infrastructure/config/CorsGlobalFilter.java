package com.gymadmin.platform.infrastructure.config;

import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class CorsGlobalFilter implements WebFilter {

    private final AppProperties appProperties;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String origin = exchange.getRequest().getHeaders().getFirst("Origin");

        if (origin != null && isAllowed(origin)) {
            var response = exchange.getResponse();
            var headers = response.getHeaders();

            headers.set("Access-Control-Allow-Origin", origin);
            headers.set("Access-Control-Allow-Credentials", "true");
            headers.set("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS");
            headers.set("Access-Control-Allow-Headers", "Content-Type,Authorization");
            headers.set("Vary", "Origin");

            if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())) {
                response.setStatusCode(HttpStatus.OK);
                return response.setComplete();
            }
        }

        return chain.filter(exchange);
    }

    private boolean isAllowed(String origin) {
        AppProperties.Cors cors = appProperties.getCors();
        if (cors.isAllowAll()) {
            return true;
        }
        List<String> allowed = cors.getAllowedOrigins();
        return allowed != null && allowed.contains(origin);
    }
}
