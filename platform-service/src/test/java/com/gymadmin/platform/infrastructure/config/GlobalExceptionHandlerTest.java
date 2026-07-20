package com.gymadmin.platform.infrastructure.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.gymadmin.platform.domain.exception.EstadoInvalidoException;
import com.gymadmin.platform.domain.exception.LimiteAlcanzadoException;
import com.gymadmin.platform.domain.exception.PagoDuplicadoException;
import com.gymadmin.platform.domain.exception.PagoYaProcesadoException;
import com.gymadmin.platform.domain.exception.RateLimitExcedidoException;
import com.gymadmin.platform.domain.exception.SinSuscripcionCancelableException;
import com.gymadmin.platform.domain.exception.SuscripcionActivaException;
import com.gymadmin.platform.domain.exception.TrialYaUsadoException;
import com.gymadmin.platform.domain.model.RecursoLimitable;
import com.gymadmin.platform.infrastructure.exception.BusinessException;
import com.gymadmin.platform.infrastructure.exception.ConflictException;
import com.gymadmin.platform.infrastructure.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica el contrato de errores estandarizado (RFC 7807 + {@code codigo}) en
 * platform-service. Cubre: mapeo de excepciones SaaS Freemium → codigo/status con
 * su metadata preservada, serialización snake_case de las extensiones (hallazgo #2 —
 * en especial {@code plan_actual}, NO {@code planActual}), traducción de constraint
 * violations (hallazgo #6) y el alias {@code mensaje}.
 */
@DisplayName("GlobalExceptionHandler — contrato de errores estandarizado (platform)")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Mismo naming strategy que el runtime (spring.jackson SNAKE_CASE).
        objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        handler = new GlobalExceptionHandler(objectMapper);
    }

    private JsonNode handleAndParse(Throwable ex) {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/companias").build());
        StepVerifier.create(handler.handle(exchange, ex)).verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        return parse(exchange);
    }

    @Test
    @DisplayName("NotFoundException → 404 codigo=recurso_no_encontrado + alias mensaje")
    void notFound() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/companias/9").build());
        StepVerifier.create(handler.handle(exchange, new NotFoundException("Compañía no existe")))
                .verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        JsonNode json = parse(exchange);
        assertThat(json.get("status").asInt()).isEqualTo(404);
        assertThat(json.get("codigo").asText()).isEqualTo("recurso_no_encontrado");
        assertThat(json.get("detail").asText()).isEqualTo("Compañía no existe");
        assertThat(json.get("mensaje").asText()).isEqualTo("Compañía no existe"); // alias
        assertThat(json.get("instance").asText()).isEqualTo("/api/v1/companias/9");
        assertThat(json.hasNonNull("timestamp")).isTrue();
    }

    @Test
    @DisplayName("ConflictException → 409 codigo=conflicto")
    void conflict() {
        JsonNode json = handleAndParse(new ConflictException("Ya existe"));
        assertThat(json.get("status").asInt()).isEqualTo(409);
        assertThat(json.get("codigo").asText()).isEqualTo("conflicto");
    }

    @Test
    @DisplayName("ConflictException con conflicto != null → añade propiedad extra conflicto")
    void conflictConMetadata() {
        JsonNode json = handleAndParse(new ConflictException("Correo en uso", "correo@x.com"));
        assertThat(json.get("status").asInt()).isEqualTo(409);
        assertThat(json.get("codigo").asText()).isEqualTo("conflicto");
        assertThat(json.get("conflicto").asText()).isEqualTo("correo@x.com");
    }

    @Test
    @DisplayName("BusinessException → 422 codigo=regla_negocio")
    void business() {
        JsonNode json = handleAndParse(new BusinessException("Regla violada"));
        assertThat(json.get("status").asInt()).isEqualTo(422);
        assertThat(json.get("codigo").asText()).isEqualTo("regla_negocio");
    }

    @Test
    @DisplayName("LimiteAlcanzadoException → 403 codigo=limite_plan_alcanzado + metadata snake_case (plan_actual)")
    void limitePlan() {
        JsonNode json = handleAndParse(
                new LimiteAlcanzadoException(RecursoLimitable.SUCURSALES, 5, 5, "FREE"));
        assertThat(json.get("status").asInt()).isEqualTo(403);
        assertThat(json.get("codigo").asText()).isEqualTo("limite_plan_alcanzado");
        assertThat(json.get("recurso").asText()).isEqualTo("SUCURSALES");
        assertThat(json.get("actual").asLong()).isEqualTo(5);
        assertThat(json.get("maximo").asLong()).isEqualTo(5);
        // hallazgo #2/riesgo #1: la clave debe ser snake_case, no camelCase.
        assertThat(json.hasNonNull("plan_actual")).isTrue();
        assertThat(json.get("plan_actual").asText()).isEqualTo("FREE");
        assertThat(json.has("planActual")).isFalse();
    }

    @Test
    @DisplayName("TrialYaUsadoException → 409 codigo=trial_ya_usado (preservado)")
    void trialYaUsado() {
        JsonNode json = handleAndParse(new TrialYaUsadoException("Ya usó su trial"));
        assertThat(json.get("status").asInt()).isEqualTo(409);
        assertThat(json.get("codigo").asText()).isEqualTo("trial_ya_usado");
    }

    @Test
    @DisplayName("SuscripcionActivaException → 409 codigo=suscripcion_activa (preservado)")
    void suscripcionActiva() {
        JsonNode json = handleAndParse(new SuscripcionActivaException("Tiene suscripción activa"));
        assertThat(json.get("status").asInt()).isEqualTo(409);
        assertThat(json.get("codigo").asText()).isEqualTo("suscripcion_activa");
    }

    @Test
    @DisplayName("SinSuscripcionCancelableException → 400 codigo=sin_suscripcion_cancelable (preservado)")
    void sinSuscripcionCancelable() {
        JsonNode json = handleAndParse(new SinSuscripcionCancelableException("No hay sub cancelable"));
        assertThat(json.get("status").asInt()).isEqualTo(400);
        assertThat(json.get("codigo").asText()).isEqualTo("sin_suscripcion_cancelable");
    }

    @Test
    @DisplayName("PagoDuplicadoException → 409 codigo=pago_duplicado (preservado)")
    void pagoDuplicado() {
        JsonNode json = handleAndParse(new PagoDuplicadoException("Pago duplicado", "abc"));
        assertThat(json.get("status").asInt()).isEqualTo(409);
        assertThat(json.get("codigo").asText()).isEqualTo("pago_duplicado");
    }

    @Test
    @DisplayName("PagoYaProcesadoException → 409 codigo=pago_ya_procesado (preservado)")
    void pagoYaProcesado() {
        JsonNode json = handleAndParse(new PagoYaProcesadoException("Ya procesado", 7L));
        assertThat(json.get("status").asInt()).isEqualTo(409);
        assertThat(json.get("codigo").asText()).isEqualTo("pago_ya_procesado");
    }

    @Test
    @DisplayName("EstadoInvalidoException → 400 codigo=transicion_invalida (preservado)")
    void estadoInvalido() {
        JsonNode json = handleAndParse(new EstadoInvalidoException("Transición no permitida"));
        assertThat(json.get("status").asInt()).isEqualTo(400);
        assertThat(json.get("codigo").asText()).isEqualTo("transicion_invalida");
    }

    @Test
    @DisplayName("RateLimitExcedidoException → 429 codigo=rate_limit_excedido + ventana/max")
    void rateLimit() {
        JsonNode json = handleAndParse(
                new RateLimitExcedidoException("máx 3 en 1h", "1h", 3));
        assertThat(json.get("status").asInt()).isEqualTo(429);
        assertThat(json.get("codigo").asText()).isEqualTo("rate_limit_excedido");
        assertThat(json.get("ventana").asText()).isEqualTo("1h");
        assertThat(json.get("max").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("DataIntegrityViolationException (unique) → 409 codigo=datos_duplicados")
    void uniqueViolation() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "insert failed",
                new RuntimeException("ERROR: duplicate key value violates unique constraint \"uq_companias_ruc\""));
        JsonNode json = handleAndParse(ex);
        assertThat(json.get("status").asInt()).isEqualTo(409);
        assertThat(json.get("codigo").asText()).isEqualTo("datos_duplicados");
        assertThat(json.get("detail").asText()).contains("uq_companias_ruc");
    }

    @Test
    @DisplayName("DataIntegrityViolationException (FK) → 409 codigo=referencia_invalida")
    void fkViolation() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "insert failed",
                new RuntimeException("ERROR: insert violates foreign key constraint \"fk_sucursal_compania\""));
        JsonNode json = handleAndParse(ex);
        assertThat(json.get("codigo").asText()).isEqualTo("referencia_invalida");
    }

    @Test
    @DisplayName("Excepción no controlada → 500 codigo=error_interno sin filtrar internos")
    void unhandled() {
        JsonNode json = handleAndParse(new RuntimeException("NPE interno con datos sensibles"));
        assertThat(json.get("status").asInt()).isEqualTo(500);
        assertThat(json.get("codigo").asText()).isEqualTo("error_interno");
        assertThat(json.get("detail").asText()).isEqualTo("Ocurrió un error inesperado");
        assertThat(json.get("detail").asText()).doesNotContain("sensibles");
    }

    private JsonNode parse(MockServerWebExchange exchange) {
        byte[] bytes = DataBufferUtils.join(exchange.getResponse().getBody())
                .map(buf -> {
                    byte[] b = new byte[buf.readableByteCount()];
                    buf.read(b);
                    DataBufferUtils.release(buf);
                    return b;
                })
                .block();
        try {
            return objectMapper.readTree(bytes != null ? bytes : new byte[]{'{', '}'});
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
