package com.gymadmin.core.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymadmin.core.infrastructure.exception.ErrorCode;
import com.gymadmin.core.infrastructure.exception.ProblemDetailFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Emite el sobre estándar ({@code codigo=no_autenticado}, 401) cuando Spring
 * Security rechaza un request sin autenticación. Sin esto, el 401 más común
 * (token ausente/expirado) escaparía al contrato (hallazgo #1).
 */
@Component
public class ApiAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public ApiAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        ProblemDetail pd = ProblemDetailFactory.create(
                ErrorCode.NO_AUTENTICADO, "Autenticación requerida", exchange);
        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(ProblemDetailFactory.toMap(pd));
        } catch (Exception e) {
            bytes = "{\"status\":401,\"codigo\":\"no_autenticado\"}".getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
