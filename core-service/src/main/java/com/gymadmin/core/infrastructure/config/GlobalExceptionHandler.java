package com.gymadmin.core.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymadmin.core.domain.exception.LimiteAlcanzadoException;
import com.gymadmin.core.infrastructure.exception.BusinessException;
import com.gymadmin.core.infrastructure.exception.CodedException;
import com.gymadmin.core.infrastructure.exception.ConflictException;
import com.gymadmin.core.infrastructure.exception.DataIntegrityMapper;
import com.gymadmin.core.infrastructure.exception.DatosVentaIncompletosException;
import com.gymadmin.core.infrastructure.exception.ErrorCode;
import com.gymadmin.core.infrastructure.exception.ForbiddenException;
import com.gymadmin.core.infrastructure.exception.NotFoundException;
import com.gymadmin.core.infrastructure.exception.ProblemDetailFactory;
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
 * <p>Ver {@code docs/gym-administrator/architecture/error-contract.md}. Todas las
 * respuestas de error (incluidas las de Spring Security vía
 * {@link ApiAuthenticationEntryPoint}/{@link ApiAccessDeniedHandler}) emiten el
 * mismo sobre. Las claves de extensión van en snake_case literal.
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
        // REQ-SAAS-001 (Sub-fase 1.4): cuota de plan alcanzada — 403 con metadata
        // de negocio al nivel raíz del sobre (el UpgradeModal la consume).
        if (ex instanceof LimiteAlcanzadoException la) {
            ProblemDetail pd = ProblemDetailFactory.create(
                    ErrorCode.LIMITE_PLAN_ALCANZADO, la.getMessage(), exchange);
            pd.setProperty("recurso", la.getRecurso());
            pd.setProperty("actual", la.getActual());
            pd.setProperty("maximo", la.getMaximo());
            pd.setProperty("plan_actual", la.getPlanCodigo());
            return pd;
        }

        // CodedException tiene precedencia sobre las excepciones genéricas: el codigo
        // de negocio va explícito (solicitud_ya_existe, membresia_activa_vigente, etc.).
        if (ex instanceof DatosVentaIncompletosException dvi) {
            ProblemDetail pd = ProblemDetailFactory.create(
                    dvi.getErrorCode(), dvi.getMessage(), exchange);
            pd.setProperty("errores", dvi.getErrores());
            return pd;
        }
        if (ex instanceof CodedException ce) {
            return ProblemDetailFactory.create(ce.getErrorCode(), ce.getMessage(), exchange);
        }
        if (ex instanceof NotFoundException) {
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
        if (ex instanceof BusinessException) {
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
                case BAD_REQUEST -> ErrorCode.VALIDACION.codigo();
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
