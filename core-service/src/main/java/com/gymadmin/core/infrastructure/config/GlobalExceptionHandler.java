package com.gymadmin.core.infrastructure.config;

import com.gymadmin.core.domain.exception.LimiteAlcanzadoException;
import com.gymadmin.core.infrastructure.exception.BusinessException;
import com.gymadmin.core.infrastructure.exception.ConflictException;
import com.gymadmin.core.infrastructure.exception.ForbiddenException;
import com.gymadmin.core.infrastructure.exception.NotFoundException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        // REQ-SAAS-001 (Sub-fase 1.4): cuota de plan alcanzada — 403 con detalle.
        if (ex instanceof LimiteAlcanzadoException la) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("codigo", "limite_plan_alcanzado");
            body.put("recurso", la.getRecurso());
            body.put("actual", la.getActual());
            body.put("maximo", la.getMaximo());
            body.put("planActual", la.getPlanCodigo());
            return writeBody(exchange, HttpStatus.FORBIDDEN, body);
        }

        HttpStatus status;
        String message;

        if (ex instanceof NotFoundException) {
            status = HttpStatus.NOT_FOUND;
            message = ex.getMessage();
        } else if (ex instanceof ConflictException) {
            status = HttpStatus.CONFLICT;
            message = ex.getMessage();
        } else if (ex instanceof ForbiddenException) {
            status = HttpStatus.FORBIDDEN;
            message = ex.getMessage();
        } else if (ex instanceof BusinessException) {
            status = HttpStatus.UNPROCESSABLE_ENTITY;
            message = ex.getMessage();
        } else if (ex instanceof org.springframework.web.server.ResponseStatusException rse) {
            status = HttpStatus.valueOf(rse.getStatusCode().value());
            message = rse.getReason() != null ? rse.getReason() : rse.getMessage();
        } else if (ex instanceof org.springframework.web.bind.support.WebExchangeBindException bindEx) {
            status = HttpStatus.BAD_REQUEST;
            message = bindEx.getBindingResult().getFieldErrors().stream()
                    .map(e -> e.getField() + ": " + e.getDefaultMessage())
                    .reduce("", (a, b) -> a.isEmpty() ? b : a + "; " + b);
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            message = "An unexpected error occurred";
            System.err.println("[GlobalExceptionHandler] " + ex.getClass().getName() + " - " + ex.getMessage());
        }

        Map<String, Object> body = Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message,
                "path", exchange.getRequest().getPath().value()
        );

        return writeBody(exchange, status, body);
    }

    private Mono<Void> writeBody(ServerWebExchange exchange, HttpStatus status, Map<String, Object> body) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }
}
