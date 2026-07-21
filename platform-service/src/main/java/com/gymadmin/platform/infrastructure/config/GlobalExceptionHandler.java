package com.gymadmin.platform.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.gymadmin.platform.infrastructure.exception.DataIntegrityMapper;
import com.gymadmin.platform.infrastructure.exception.ErrorCode;
import com.gymadmin.platform.infrastructure.exception.ForbiddenException;
import com.gymadmin.platform.infrastructure.exception.NotFoundException;
import com.gymadmin.platform.infrastructure.exception.ProblemDetailFactory;
import com.gymadmin.platform.infrastructure.exception.RecordatorioNoEnviableException;
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
 *
 * <p>Las excepciones de dominio SaaS Freemium (límite de plan, trial, pagos,
 * rate limit) conservan su {@code codigo} exacto y su metadata de negocio se
 * expone como propiedades extra al nivel raíz del sobre (el {@code UpgradeModal}
 * y otras UIs las consumen).
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
        // --- Excepciones SaaS Freemium (codigo + metadata preservados exactos) ---
        if (ex instanceof LimiteAlcanzadoException la) {
            ProblemDetail pd = ProblemDetailFactory.create(
                    ErrorCode.LIMITE_PLAN_ALCANZADO, la.getMessage(), exchange);
            pd.setProperty("recurso", la.getRecurso() != null ? la.getRecurso().name() : null);
            pd.setProperty("actual", la.getActual());
            pd.setProperty("maximo", la.getMaximo());
            pd.setProperty("plan_actual", la.getPlanCodigo());
            return pd;
        }
        if (ex instanceof TrialYaUsadoException) {
            return ProblemDetailFactory.create(ErrorCode.TRIAL_YA_USADO, ex.getMessage(), exchange);
        }
        if (ex instanceof SuscripcionActivaException) {
            return ProblemDetailFactory.create(ErrorCode.SUSCRIPCION_ACTIVA, ex.getMessage(), exchange);
        }
        if (ex instanceof SinSuscripcionCancelableException) {
            return ProblemDetailFactory.create(ErrorCode.SIN_SUSCRIPCION_CANCELABLE, ex.getMessage(), exchange);
        }
        if (ex instanceof PagoDuplicadoException) {
            return ProblemDetailFactory.create(ErrorCode.PAGO_DUPLICADO, ex.getMessage(), exchange);
        }
        if (ex instanceof PagoYaProcesadoException) {
            return ProblemDetailFactory.create(ErrorCode.PAGO_YA_PROCESADO, ex.getMessage(), exchange);
        }
        if (ex instanceof EstadoInvalidoException) {
            return ProblemDetailFactory.create(ErrorCode.TRANSICION_INVALIDA, ex.getMessage(), exchange);
        }
        if (ex instanceof RateLimitExcedidoException rle) {
            ProblemDetail pd = ProblemDetailFactory.create(
                    ErrorCode.RATE_LIMIT_EXCEDIDO, ex.getMessage(), exchange);
            pd.setProperty("ventana", rle.getVentana());
            pd.setProperty("max", rle.getMax());
            return pd;
        }

        // --- Excepciones de infraestructura comunes ---
        if (ex instanceof NotFoundException) {
            return ProblemDetailFactory.create(ErrorCode.RECURSO_NO_ENCONTRADO, ex.getMessage(), exchange);
        }
        if (ex instanceof ConflictException conflictEx) {
            ProblemDetail pd = ProblemDetailFactory.create(ErrorCode.CONFLICTO, ex.getMessage(), exchange);
            if (conflictEx.getConflicto() != null) {
                pd.setProperty("conflicto", conflictEx.getConflicto());
            }
            return pd;
        }
        if (ex instanceof ForbiddenException) {
            return ProblemDetailFactory.create(ErrorCode.ACCESO_DENEGADO, ex.getMessage(), exchange);
        }
        if (ex instanceof AccessDeniedException) {
            return ProblemDetailFactory.create(ErrorCode.ACCESO_DENEGADO, ex.getMessage(), exchange);
        }
        if (ex instanceof RecordatorioNoEnviableException rne) {
            return ProblemDetailFactory.create(rne.getErrorCode(), ex.getMessage(), exchange);
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
        if (ex.getCause() != null) {
            System.err.println("[GlobalExceptionHandler] Cause: "
                    + ex.getCause().getClass().getName() + " - " + ex.getCause().getMessage());
        }
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
