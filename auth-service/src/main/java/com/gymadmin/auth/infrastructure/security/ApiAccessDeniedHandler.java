package com.gymadmin.auth.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymadmin.auth.infrastructure.exception.ErrorCode;
import com.gymadmin.auth.infrastructure.exception.ProblemDetailFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Emite el sobre estándar ({@code codigo=acceso_denegado}, 403) cuando Spring
 * Security rechaza un request por falta de autorización (hallazgo #1 del
 * contrato de errores estandarizado).
 */
@Component
@RequiredArgsConstructor
public class ApiAccessDeniedHandler implements ServerAccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException ex) {
        ProblemDetail pd = ProblemDetailFactory.create(
                ErrorCode.ACCESO_DENEGADO, "Acceso denegado", exchange);
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(ProblemDetailFactory.toMap(pd));
        } catch (Exception e) {
            bytes = "{\"status\":403,\"codigo\":\"acceso_denegado\"}".getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
