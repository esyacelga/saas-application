package com.gymadmin.auth.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymadmin.auth.domain.exception.*;
import com.gymadmin.auth.exception.ApiError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status;
        ApiError error;

        if (ex instanceof AuthException e) {
            status = HttpStatus.UNAUTHORIZED;
            error  = ApiError.of(401, "Unauthorized", e.getMessage());
        } else if (ex instanceof ConflictException e) {
            status = HttpStatus.CONFLICT;
            error  = ApiError.of(409, "Conflict", e.getMessage());
        } else if (ex instanceof ResourceNotFoundException e) {
            status = HttpStatus.NOT_FOUND;
            error  = ApiError.of(404, "Not Found", e.getMessage());
        } else if (ex instanceof ForbiddenException e) {
            status = HttpStatus.FORBIDDEN;
            error  = ApiError.of(403, "Forbidden", e.getMessage());
        } else if (ex instanceof TooManyRequestsException e) {
            status = HttpStatus.TOO_MANY_REQUESTS;
            error  = ApiError.of(429, "Too Many Requests", e.getMessage());
        } else if (ex instanceof BadRequestException e) {
            status = HttpStatus.BAD_REQUEST;
            error  = ApiError.of(400, "Bad Request", e.getMessage());
        } else if (ex instanceof IllegalArgumentException e) {
            status = HttpStatus.BAD_REQUEST;
            error  = ApiError.of(400, "Bad Request", e.getMessage());
        } else if (ex instanceof DataIntegrityViolationException e) {
            status = HttpStatus.CONFLICT;
            String detail = resolveConstraintMessage(e);
            log.warn("DataIntegrityViolationException: {}", rootMessage(e));
            error  = ApiError.of(409, "Conflict", detail);
        } else {
            log.error("Unhandled exception [{}]: {}", ex.getClass().getName(), ex.getMessage(), ex);
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            error  = ApiError.of(500, "Internal Server Error", "Error interno del servidor");
        }

        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(error);
        } catch (Exception e) {
            bytes = ("{\"status\":500,\"error\":\"Internal Server Error\"}").getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    private String rootMessage(DataIntegrityViolationException ex) {
        Throwable cause = ex.getMostSpecificCause();
        return cause != null ? cause.getMessage() : ex.getMessage();
    }

    private String resolveConstraintMessage(DataIntegrityViolationException ex) {
        String msg = rootMessage(ex);
        if (msg == null) return "Violación de restricción de integridad de datos";
        String lower = msg.toLowerCase();

        if (lower.contains("not-null constraint") || lower.contains("violates not-null")) {
            Matcher m = Pattern.compile("column \"(\\w+)\"").matcher(msg);
            if (m.find()) return "El campo '" + m.group(1) + "' es requerido y no puede ser nulo";
            return "Un campo obligatorio no fue proporcionado";
        }
        if (lower.contains("unique constraint") || lower.contains("duplicate key")) {
            Matcher m = Pattern.compile("constraint \"([\\w]+)\"").matcher(msg);
            if (m.find()) return "Registro duplicado (restricción: " + m.group(1) + ")";
            return "Registro duplicado: ya existe un elemento con los datos proporcionados";
        }
        if (lower.contains("foreign key constraint")) {
            Matcher m = Pattern.compile("constraint \"([\\w]+)\"").matcher(msg);
            if (m.find()) return "Referencia inválida: el registro relacionado no existe (restricción: " + m.group(1) + ")";
            return "Referencia inválida: el registro relacionado no existe";
        }
        return "Violación de restricción de integridad de datos: " + msg;
    }
}
