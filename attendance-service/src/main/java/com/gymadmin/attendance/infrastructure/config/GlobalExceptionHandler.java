package com.gymadmin.attendance.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymadmin.attendance.infrastructure.exception.ConflictException;
import com.gymadmin.attendance.infrastructure.exception.DataIntegrityMapper;
import com.gymadmin.attendance.infrastructure.exception.ErrorCode;
import com.gymadmin.attendance.infrastructure.exception.ForbiddenException;
import com.gymadmin.attendance.infrastructure.exception.GoneException;
import com.gymadmin.attendance.infrastructure.exception.IllegalArgumentException;
import com.gymadmin.attendance.infrastructure.exception.NotFoundException;
import com.gymadmin.attendance.infrastructure.exception.ProblemDetailFactory;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
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
 * <p>Reemplaza al antiguo {@code @RestControllerAdvice} (que no capturaba errores
 * de filtros/routing) por un {@link ErrorWebExceptionHandler} con
 * {@code @Order(HIGHEST_PRECEDENCE)}. Ver
 * {@code docs/gym-administrator/architecture/error-contract.md}. Todas las
 * respuestas de error (incluidas las de Spring Security vía
 * {@link ApiAuthenticationEntryPoint}/{@link ApiAccessDeniedHandler}) emiten el
 * mismo sobre. Las claves de extensión van en snake_case literal.
 *
 * <p><b>Contrato PWA (congelado):</b> {@link ConflictException} preserva su
 * {@code getCodigo()} EXACTO en el campo {@code codigo} del sobre
 * ({@code ya_registrado_hoy}, {@code sin_membresia}, {@code ultima_plantilla},
 * ...). El PWA de socios hace branching de UI sobre esos strings — no se
 * traducen (hallazgo #3).
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ProblemDetail pd = toProblemDetail(exchange, ex);
        return write(exchange, pd);
    }

    private ProblemDetail toProblemDetail(ServerWebExchange exchange, Throwable ex) {
        if (ex instanceof NotFoundException) {
            return ProblemDetailFactory.create(ErrorCode.RECURSO_NO_ENCONTRADO, ex.getMessage(), exchange);
        }
        if (ex instanceof ForbiddenException) {
            return ProblemDetailFactory.create(ErrorCode.ACCESO_DENEGADO, ex.getMessage(), exchange);
        }
        if (ex instanceof AccessDeniedException) {
            return ProblemDetailFactory.create(ErrorCode.ACCESO_DENEGADO, ex.getMessage(), exchange);
        }
        // Contrato PWA (hallazgo #3): el codigo de la excepción se emite TAL CUAL
        // (ya_registrado_hoy, sin_membresia, ...). NO se traduce.
        if (ex instanceof ConflictException ce) {
            String codigo = ce.getCodigo() != null ? ce.getCodigo() : ErrorCode.CONFLICTO.codigo();
            return ProblemDetailFactory.create(HttpStatus.CONFLICT, codigo, ce.getMessage(), exchange);
        }
        if (ex instanceof GoneException) {
            return ProblemDetailFactory.create(ErrorCode.RECURSO_NO_DISPONIBLE, ex.getMessage(), exchange);
        }
        // Custom IllegalArgumentException (infrastructure.exception) → 422 regla_negocio.
        if (ex instanceof IllegalArgumentException) {
            return ProblemDetailFactory.create(ErrorCode.REGLA_NEGOCIO, ex.getMessage(), exchange);
        }
        if (ex instanceof DataIntegrityViolationException dive) {
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
        if (ex instanceof ResponseStatusException rse) {
            HttpStatus status = HttpStatus.valueOf(rse.getStatusCode().value());
            String detail = rse.getReason() != null ? rse.getReason() : rse.getMessage();
            String codigo = switch (status) {
                case NOT_FOUND -> ErrorCode.RECURSO_NO_ENCONTRADO.codigo();
                case CONFLICT -> ErrorCode.CONFLICTO.codigo();
                case FORBIDDEN -> ErrorCode.ACCESO_DENEGADO.codigo();
                case UNAUTHORIZED -> ErrorCode.NO_AUTENTICADO.codigo();
                case GONE -> ErrorCode.RECURSO_NO_DISPONIBLE.codigo();
                case BAD_REQUEST -> ErrorCode.VALIDACION.codigo();
                case UNPROCESSABLE_ENTITY -> ErrorCode.REGLA_NEGOCIO.codigo();
                case TOO_MANY_REQUESTS -> ErrorCode.DEMASIADAS_SOLICITUDES.codigo();
                default -> ErrorCode.ERROR_INTERNO.codigo();
            };
            return ProblemDetailFactory.create(status, codigo, detail, exchange);
        }

        // No controlado: no filtrar internos.
        System.err.println("[GlobalExceptionHandler] " + ex.getClass().getName() + " - " + ex.getMessage());
        return ProblemDetailFactory.create(ErrorCode.ERROR_INTERNO, "Ocurrió un error inesperado", exchange);
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
