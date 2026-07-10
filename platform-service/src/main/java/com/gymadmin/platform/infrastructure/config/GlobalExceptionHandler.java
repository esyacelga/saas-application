package com.gymadmin.platform.infrastructure.config;

import com.gymadmin.platform.domain.exception.EstadoInvalidoException;
import com.gymadmin.platform.domain.exception.LimiteAlcanzadoException;
import com.gymadmin.platform.domain.exception.PagoDuplicadoException;
import com.gymadmin.platform.domain.exception.PagoYaProcesadoException;
import com.gymadmin.platform.domain.exception.RateLimitExcedidoException;
import com.gymadmin.platform.domain.exception.SinSuscripcionCancelableException;
import com.gymadmin.platform.domain.exception.SuscripcionActivaException;
import com.gymadmin.platform.domain.exception.TrialYaUsadoException;
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
        // REQ-SAAS-001 (Sub-fase 1.4): mapeos de excepciones de dominio Freemium.
        Mono<Void> saasResponse = handleSaasException(exchange, ex);
        if (saasResponse != null) {
            return saasResponse;
        }

        HttpStatus status;
        String message;

        if (ex instanceof NotFoundException) {
            status = HttpStatus.NOT_FOUND;
            message = ex.getMessage();
        } else if (ex instanceof ConflictException conflictEx) {
            status = HttpStatus.CONFLICT;
            message = ex.getMessage();
            if (conflictEx.getConflicto() != null) {
                Map<String, Object> conflictBody = new LinkedHashMap<>();
                conflictBody.put("timestamp", LocalDateTime.now().toString());
                conflictBody.put("status", status.value());
                conflictBody.put("error", status.getReasonPhrase());
                conflictBody.put("message", message);
                conflictBody.put("conflicto", conflictEx.getConflicto());
                conflictBody.put("path", exchange.getRequest().getPath().value());
                return writeBody(exchange, status, conflictBody);
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
        } else if (ex instanceof org.springframework.security.access.AccessDeniedException) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("codigo", "acceso_denegado");
            body.put("mensaje", ex.getMessage() != null ? ex.getMessage() : "Access denied");
            return writeBody(exchange, HttpStatus.FORBIDDEN, body);
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            message = "An unexpected error occurred";
            System.err.println("[GlobalExceptionHandler] Unhandled exception: " + ex.getClass().getName() + " - " + ex.getMessage());
            if (ex.getCause() != null) {
                System.err.println("[GlobalExceptionHandler] Cause: " + ex.getCause().getClass().getName() + " - " + ex.getCause().getMessage());
            }
        }

        Map<String, Object> body = defaultBody(status, message, exchange);
        return writeBody(exchange, status, body);
    }

    /**
     * REQ-SAAS-001 (Sub-fase 1.4): mapeos específicos del dominio Freemium.
     * Retorna {@code null} si la excepción no corresponde a este dominio.
     */
    private Mono<Void> handleSaasException(ServerWebExchange exchange, Throwable ex) {
        if (ex instanceof LimiteAlcanzadoException la) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("codigo", "limite_plan_alcanzado");
            body.put("recurso", la.getRecurso() != null ? la.getRecurso().name() : null);
            body.put("actual", la.getActual());
            body.put("maximo", la.getMaximo());
            body.put("planActual", la.getPlanCodigo());
            return writeBody(exchange, HttpStatus.FORBIDDEN, body);
        }
        if (ex instanceof TrialYaUsadoException) {
            return writeCodigoBody(exchange, HttpStatus.CONFLICT, "trial_ya_usado", ex.getMessage());
        }
        if (ex instanceof SuscripcionActivaException) {
            return writeCodigoBody(exchange, HttpStatus.CONFLICT, "suscripcion_activa", ex.getMessage());
        }
        if (ex instanceof SinSuscripcionCancelableException) {
            return writeCodigoBody(exchange, HttpStatus.BAD_REQUEST, "sin_suscripcion_cancelable", ex.getMessage());
        }
        if (ex instanceof PagoDuplicadoException) {
            return writeCodigoBody(exchange, HttpStatus.CONFLICT, "pago_duplicado", ex.getMessage());
        }
        if (ex instanceof PagoYaProcesadoException) {
            return writeCodigoBody(exchange, HttpStatus.CONFLICT, "pago_ya_procesado", ex.getMessage());
        }
        if (ex instanceof EstadoInvalidoException) {
            return writeCodigoBody(exchange, HttpStatus.BAD_REQUEST, "transicion_invalida", ex.getMessage());
        }
        if (ex instanceof RateLimitExcedidoException rle) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("codigo", "rate_limit_excedido");
            body.put("ventana", rle.getVentana());
            body.put("max", rle.getMax());
            body.put("mensaje", ex.getMessage());
            return writeBody(exchange, HttpStatus.TOO_MANY_REQUESTS, body);
        }
        return null;
    }

    private Mono<Void> writeCodigoBody(ServerWebExchange exchange, HttpStatus status, String codigo, String mensaje) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("codigo", codigo);
        body.put("mensaje", mensaje);
        return writeBody(exchange, status, body);
    }

    private Map<String, Object> defaultBody(HttpStatus status, String message, ServerWebExchange exchange) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", exchange.getRequest().getPath().value());
        return body;
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
