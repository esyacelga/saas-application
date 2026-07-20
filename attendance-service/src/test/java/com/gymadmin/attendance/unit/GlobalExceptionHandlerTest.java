package com.gymadmin.attendance.unit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.gymadmin.attendance.infrastructure.config.GlobalExceptionHandler;
import com.gymadmin.attendance.infrastructure.exception.ConflictException;
import com.gymadmin.attendance.infrastructure.exception.GoneException;
import com.gymadmin.attendance.infrastructure.exception.IllegalArgumentException;
import com.gymadmin.attendance.infrastructure.exception.NotFoundException;
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
 * extensiones (hallazgo #2), traducción de constraint violations (hallazgo #6),
 * el 410 de {@link GoneException}, y — crítico — la preservación EXACTA de los
 * {@code codigo} congelados que consume el PWA (hallazgo #3).
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
                MockServerHttpRequest.get("/api/v1/asistencias").build());
        StepVerifier.create(handler.handle(exchange, ex)).verifyComplete();

        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);

        return parse(exchange);
    }

    @Test
    @DisplayName("NotFoundException → 404 codigo=recurso_no_encontrado")
    void notFound() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/asistencias/9").build());
        StepVerifier.create(handler.handle(exchange, new NotFoundException("Asistencia no existe")))
                .verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        JsonNode json = parse(exchange);
        assertThat(json.get("status").asInt()).isEqualTo(404);
        assertThat(json.get("codigo").asText()).isEqualTo("recurso_no_encontrado");
        assertThat(json.get("detail").asText()).isEqualTo("Asistencia no existe");
        assertThat(json.get("mensaje").asText()).isEqualTo("Asistencia no existe"); // alias
        assertThat(json.get("instance").asText()).isEqualTo("/api/v1/asistencias/9");
        assertThat(json.hasNonNull("timestamp")).isTrue();
    }

    @Test
    @DisplayName("ConflictException genérico → 409 codigo=conflicto (fallback)")
    void conflictFallback() {
        JsonNode json = handleAndParse(new ConflictException("conflicto", "Ya existe"));
        assertThat(json.get("status").asInt()).isEqualTo(409);
        assertThat(json.get("codigo").asText()).isEqualTo("conflicto");
    }

    @Test
    @DisplayName("ConflictException con codigo null → 409 codigo=conflicto")
    void conflictNullCodigo() {
        JsonNode json = handleAndParse(new ConflictException(null, "Ya existe"));
        assertThat(json.get("status").asInt()).isEqualTo(409);
        assertThat(json.get("codigo").asText()).isEqualTo("conflicto");
    }

    @Test
    @DisplayName("PWA CONGELADO: ConflictException(ya_registrado_hoy) preserva el codigo EXACTO")
    void frozenYaRegistradoHoy() {
        JsonNode json = handleAndParse(
                new ConflictException("ya_registrado_hoy", "El socio ya registró asistencia hoy"));
        assertThat(json.get("status").asInt()).isEqualTo(409);
        // El PWA hace branching de UI sobre este string exacto — NO debe traducirse.
        assertThat(json.get("codigo").asText()).isEqualTo("ya_registrado_hoy");
        assertThat(json.get("detail").asText()).isEqualTo("El socio ya registró asistencia hoy");
    }

    @Test
    @DisplayName("PWA CONGELADO: ConflictException(sin_membresia) preserva el codigo EXACTO")
    void frozenSinMembresia() {
        JsonNode json = handleAndParse(
                new ConflictException("sin_membresia", "El socio no tiene membresía activa"));
        assertThat(json.get("status").asInt()).isEqualTo(409);
        assertThat(json.get("codigo").asText()).isEqualTo("sin_membresia");
    }

    @Test
    @DisplayName("PWA CONGELADO: ultima_plantilla preserva el codigo EXACTO")
    void frozenUltimaPlantilla() {
        JsonNode json = handleAndParse(
                new ConflictException("ultima_plantilla", "No se puede eliminar la última plantilla"));
        assertThat(json.get("codigo").asText()).isEqualTo("ultima_plantilla");
    }

    @Test
    @DisplayName("GoneException → 410 codigo=recurso_no_disponible")
    void gone() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/asistencias/qr").build());
        StepVerifier.create(handler.handle(exchange, new GoneException("El QR ha expirado")))
                .verifyComplete();
        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.GONE);

        JsonNode json = parse(exchange);
        assertThat(json.get("status").asInt()).isEqualTo(410);
        assertThat(json.get("codigo").asText()).isEqualTo("recurso_no_disponible");
        assertThat(json.get("detail").asText()).isEqualTo("El QR ha expirado");
    }

    @Test
    @DisplayName("IllegalArgumentException custom → 422 codigo=regla_negocio")
    void reglaNegocio() {
        JsonNode json = handleAndParse(new IllegalArgumentException("Regla violada"));
        assertThat(json.get("status").asInt()).isEqualTo(422);
        assertThat(json.get("codigo").asText()).isEqualTo("regla_negocio");
    }

    @Test
    @DisplayName("DataIntegrityViolationException (unique) → 409 codigo=datos_duplicados")
    void uniqueViolation() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "insert failed",
                new RuntimeException("ERROR: duplicate key value violates unique constraint \"uq_asistencia_dia\""));
        JsonNode json = handleAndParse(ex);
        assertThat(json.get("status").asInt()).isEqualTo(409);
        assertThat(json.get("codigo").asText()).isEqualTo("datos_duplicados");
        assertThat(json.get("detail").asText()).contains("uq_asistencia_dia");
    }

    @Test
    @DisplayName("DataIntegrityViolationException (FK) → 409 codigo=referencia_invalida")
    void fkViolation() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "insert failed",
                new RuntimeException("ERROR: insert violates foreign key constraint \"fk_asistencia_cliente\""));
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
