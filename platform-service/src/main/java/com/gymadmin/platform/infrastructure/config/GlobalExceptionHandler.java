package com.gymadmin.platform.infrastructure.config;

import com.gymadmin.platform.infrastructure.exception.BusinessException;
import com.gymadmin.platform.infrastructure.exception.ConflictException;
import com.gymadmin.platform.infrastructure.exception.ForbiddenException;
import com.gymadmin.platform.infrastructure.exception.NotFoundException;
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
        HttpStatus status;
        String message;

        if (ex instanceof NotFoundException) {
            status = HttpStatus.NOT_FOUND;
            message = ex.getMessage();
        } else if (ex instanceof ConflictException conflictEx) {
            status = HttpStatus.CONFLICT;
            message = ex.getMessage();
            if (conflictEx.getConflicto() != null) {
                Map<String, Object> conflictBody = new java.util.LinkedHashMap<>();
                conflictBody.put("timestamp", LocalDateTime.now().toString());
                conflictBody.put("status", status.value());
                conflictBody.put("error", status.getReasonPhrase());
                conflictBody.put("message", message);
                conflictBody.put("conflicto", conflictEx.getConflicto());
                conflictBody.put("path", exchange.getRequest().getPath().value());
                exchange.getResponse().setStatusCode(status);
                exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                try {
                    byte[] bytes = objectMapper.writeValueAsBytes(conflictBody);
                    DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
                    return exchange.getResponse().writeWith(Mono.just(buffer));
                } catch (Exception e) {
                    return Mono.error(e);
                }
            }
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
            // Log para diagnóstico
            System.err.println("[GlobalExceptionHandler] Unhandled exception: " + ex.getClass().getName() + " - " + ex.getMessage());
            if (ex.getCause() != null) {
                System.err.println("[GlobalExceptionHandler] Cause: " + ex.getCause().getClass().getName() + " - " + ex.getCause().getMessage());
            }
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message,
                "path", exchange.getRequest().getPath().value()
        );

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(body);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }
}
