package com.gymadmin.auth.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymadmin.auth.domain.exception.AuthException;
import com.gymadmin.auth.domain.exception.BadRequestException;
import com.gymadmin.auth.domain.exception.ConflictException;
import com.gymadmin.auth.domain.exception.ForbiddenException;
import com.gymadmin.auth.domain.exception.ResourceNotFoundException;
import com.gymadmin.auth.domain.exception.TooManyRequestsException;
import com.gymadmin.auth.infrastructure.exception.DataIntegrityMapper;
import com.gymadmin.auth.infrastructure.exception.ErrorCode;
import com.gymadmin.auth.infrastructure.exception.ProblemDetailFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler global de errores — contrato estandarizado RFC 7807 ({@link ProblemDetail})
 * + campo {@code codigo}. Punto único de salida de errores del servicio.
 *
 * <p>Ver {@code docs/gym-administrator/architecture/error-contract.md}. Todas las
 * respuestas de error (incluidas las de Spring Security vía
 * {@code JwtAuthenticationEntryPoint}/{@code ApiAccessDeniedHandler}) emiten el
 * mismo sobre. Las claves de extensión van en snake_case literal.
 */
@Slf4j
@Component
@Order(-2)
@RequiredArgsConstructor
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ProblemDetail pd = toProblemDetail(exchange, ex);
        return write(exchange, pd);
    }

    private ProblemDetail toProblemDetail(ServerWebExchange exchange, Throwable ex) {
        if (ex instanceof AuthException) {
            return ProblemDetailFactory.create(ErrorCode.NO_AUTENTICADO, ex.getMessage(), exchange);
        }
        if (ex instanceof ResourceNotFoundException) {
            return ProblemDetailFactory.create(ErrorCode.RECURSO_NO_ENCONTRADO, ex.getMessage(), exchange);
        }
        if (ex instanceof ConflictException) {
            return ProblemDetailFactory.create(ErrorCode.CONFLICTO, ex.getMessage(), exchange);
        }
        if (ex instanceof ForbiddenException) {
            return ProblemDetailFactory.create(ErrorCode.ACCESO_DENEGADO, ex.getMessage(), exchange);
        }
        if (ex instanceof AccessDeniedException) {
            return ProblemDetailFactory.create(ErrorCode.ACCESO_DENEGADO, ex.getMessage(), exchange);
        }
        if (ex instanceof TooManyRequestsException) {
            return ProblemDetailFactory.create(ErrorCode.DEMASIADAS_SOLICITUDES, ex.getMessage(), exchange);
        }
        if (ex instanceof DataIntegrityViolationException dive) {
            // Lógica preservada desde auth-service (única en el monorepo): traduce
            // constraints de PostgreSQL a codigo + mensaje legible (hallazgo #6).
            log.warn("DataIntegrityViolationException: {}", DataIntegrityMapper.rootMessage(dive));
            DataIntegrityMapper.Resolved r = DataIntegrityMapper.resolve(dive);
            return ProblemDetailFactory.create(r.code(), r.detail(), exchange);
        }
        if (ex instanceof WebExchangeBindException bindEx) {
            List<Map<String, String>> errores = bindEx.getBindingResult().getFieldErrors().stream()
                    .map(fe -> {
                        Map<String, String> m = new LinkedHashMap<>();
                        m.put("campo", fe.getField());
                        m.put("mensaje", fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "inválido");
                        return m;
                    })
                    .toList();
            // detail conserva un resumen legible para UIs que solo muestran detail (hallazgo #8).
            String detail = errores.stream()
                    .map(m -> m.get("campo") + ": " + m.get("mensaje"))
                    .reduce("", (a, b) -> a.isEmpty() ? b : a + "; " + b);
            return ProblemDetailFactory.validacion(detail, errores, exchange);
        }
        if (ex instanceof BadRequestException || ex instanceof IllegalArgumentException) {
            return ProblemDetailFactory.create(ErrorCode.VALIDACION, ex.getMessage(), exchange);
        }

        // No controlado: no filtrar internos.
        log.error("Unhandled exception [{}]: {}", ex.getClass().getName(), ex.getMessage(), ex);
        return ProblemDetailFactory.create(ErrorCode.ERROR_INTERNO, "Error interno del servidor", exchange);
    }

    private Mono<Void> write(ServerWebExchange exchange, ProblemDetail pd) {
        HttpStatus status = HttpStatus.valueOf(pd.getStatus());
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(ProblemDetailFactory.toMap(pd));
        } catch (Exception e) {
            bytes = "{\"status\":500,\"codigo\":\"error_interno\"}".getBytes(StandardCharsets.UTF_8);
        }
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }
}
