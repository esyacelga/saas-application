package com.gymadmin.auth.unit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.gymadmin.auth.domain.exception.AuthException;
import com.gymadmin.auth.domain.exception.BadRequestException;
import com.gymadmin.auth.domain.exception.ConflictException;
import com.gymadmin.auth.domain.exception.ForbiddenException;
import com.gymadmin.auth.domain.exception.ResourceNotFoundException;
import com.gymadmin.auth.domain.exception.TooManyRequestsException;
import com.gymadmin.auth.infrastructure.config.GlobalExceptionHandler;
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
 * Verifica el contrato de errores estandarizado (RFC 7807 + {@code codigo}).
 * Cubre: mapeo de excepciones → codigo/status, serialización snake_case de las
 * extensiones (hallazgo #2), y traducción de constraint violations (hallazgo #6,
 * lógica preservada desde auth-service).
 */
@DisplayName("GlobalExceptionHandler — contrato de errores estandarizado")
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
                MockServerHttpRequest.get("/api/v1/usuarios").build());
        StepVerifier.create(handler.handle(exchange, ex)).verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        return parse(exchange);
    }

    @Test
    @DisplayName("AuthException → 401 codigo=no_autenticado")
    void authException() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/usuarios/9").build());
        StepVerifier.create(handler.handle(exchange, new AuthException("Credenciales inválidas")))
                .verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        JsonNode json = parse(exchange);
        assertThat(json.get("status").asInt()).isEqualTo(401);
        assertThat(json.get("codigo").asText()).isEqualTo("no_autenticado");
        assertThat(json.get("detail").asText()).isEqualTo("Credenciales inválidas");
        assertThat(json.get("mensaje").asText()).isEqualTo("Credenciales inválidas"); // alias
        assertThat(json.get("instance").asText()).isEqualTo("/api/v1/usuarios/9");
        assertThat(json.hasNonNull("timestamp")).isTrue();
    }

    @Test
    @DisplayName("ResourceNotFoundException → 404 codigo=recurso_no_encontrado")
    void notFound() {
        JsonNode json = handleAndParse(new ResourceNotFoundException("Usuario no existe"));
        assertThat(json.get("status").asInt()).isEqualTo(404);
        assertThat(json.get("codigo").asText()).isEqualTo("recurso_no_encontrado");
    }

    @Test
    @DisplayName("ConflictException → 409 codigo=conflicto")
    void conflict() {
        JsonNode json = handleAndParse(new ConflictException("Ya existe"));
        assertThat(json.get("status").asInt()).isEqualTo(409);
        assertThat(json.get("codigo").asText()).isEqualTo("conflicto");
    }

    @Test
    @DisplayName("ForbiddenException → 403 codigo=acceso_denegado")
    void forbidden() {
        JsonNode json = handleAndParse(new ForbiddenException("Sin permiso"));
        assertThat(json.get("status").asInt()).isEqualTo(403);
        assertThat(json.get("codigo").asText()).isEqualTo("acceso_denegado");
    }

    @Test
    @DisplayName("TooManyRequestsException → 429 codigo=demasiadas_solicitudes")
    void tooManyRequests() {
        JsonNode json = handleAndParse(new TooManyRequestsException("Rate limit"));
        assertThat(json.get("status").asInt()).isEqualTo(429);
        assertThat(json.get("codigo").asText()).isEqualTo("demasiadas_solicitudes");
    }

    @Test
    @DisplayName("BadRequestException → 400 codigo=validacion")
    void badRequest() {
        JsonNode json = handleAndParse(new BadRequestException("Dato inválido"));
        assertThat(json.get("status").asInt()).isEqualTo(400);
        assertThat(json.get("codigo").asText()).isEqualTo("validacion");
    }

    @Test
    @DisplayName("IllegalArgumentException → 400 codigo=validacion")
    void illegalArgument() {
        JsonNode json = handleAndParse(new IllegalArgumentException("Argumento inválido"));
        assertThat(json.get("status").asInt()).isEqualTo(400);
        assertThat(json.get("codigo").asText()).isEqualTo("validacion");
    }

    @Test
    @DisplayName("DataIntegrityViolationException (unique) → 409 codigo=datos_duplicados (lógica preservada)")
    void uniqueViolation() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "insert failed",
                new RuntimeException("ERROR: duplicate key value violates unique constraint \"uq_usuarios_correo\""));
        JsonNode json = handleAndParse(ex);
        assertThat(json.get("status").asInt()).isEqualTo(409);
        assertThat(json.get("codigo").asText()).isEqualTo("datos_duplicados");
        assertThat(json.get("detail").asText()).contains("uq_usuarios_correo");
    }

    @Test
    @DisplayName("DataIntegrityViolationException (FK) → 409 codigo=referencia_invalida (lógica preservada)")
    void fkViolation() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "insert failed",
                new RuntimeException("ERROR: insert violates foreign key constraint \"fk_usuario_persona\""));
        JsonNode json = handleAndParse(ex);
        assertThat(json.get("status").asInt()).isEqualTo(409);
        assertThat(json.get("codigo").asText()).isEqualTo("referencia_invalida");
        assertThat(json.get("detail").asText()).contains("fk_usuario_persona");
    }

    @Test
    @DisplayName("DataIntegrityViolationException (not-null) → 409 codigo=campo_requerido (lógica preservada)")
    void notNullViolation() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "insert failed",
                new RuntimeException("ERROR: null value in column \"correo\" violates not-null constraint"));
        JsonNode json = handleAndParse(ex);
        assertThat(json.get("codigo").asText()).isEqualTo("campo_requerido");
        assertThat(json.get("detail").asText()).contains("correo");
    }

    @Test
    @DisplayName("Excepción no controlada → 500 codigo=error_interno sin filtrar internos")
    void unhandled() {
        JsonNode json = handleAndParse(new RuntimeException("NPE interno con datos sensibles"));
        assertThat(json.get("status").asInt()).isEqualTo(500);
        assertThat(json.get("codigo").asText()).isEqualTo("error_interno");
        assertThat(json.get("detail").asText()).isEqualTo("Error interno del servidor");
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
