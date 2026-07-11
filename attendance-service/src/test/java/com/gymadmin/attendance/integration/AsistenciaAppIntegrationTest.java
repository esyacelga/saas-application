package com.gymadmin.attendance.integration;

import com.gymadmin.attendance.BaseIntegrationTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.Map;

/**
 * Tests de integración para POST /api/v1/asistencias/app.
 * Los casos TC-APP-HAPPY y TC-APP-CONFLICT requieren que el Core Service
 * esté activo en CORE_SERVICE_URL (por defecto http://localhost:8082).
 * Los casos de seguridad y validación son independientes del Core Service.
 */
@DisplayName("Asistencia App — integración endpoint POST /asistencias/app")
class AsistenciaAppIntegrationTest extends BaseIntegrationTest {

    private static final Integer COMPANIA = 1;
    private static final Integer SUCURSAL = 1;

    // ── TC-APP-SEC-001 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Sin JWT retorna 401")
    void sinJwtRetorna401() {
        webTestClient.post()
                .uri("/api/v1/asistencias/app")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("id_sucursal", SUCURSAL))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── TC-APP-SEC-002 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("JWT de staff (recepción) retorna 403 — solo clientes pueden usar este endpoint")
    void staffRetorna403() {
        webTestClient.post()
                .uri("/api/v1/asistencias/app")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtRecepcion(COMPANIA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("id_sucursal", SUCURSAL))
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── TC-APP-SEC-003 ────────────────────────────────────────────────────────

    @Test
    @DisplayName("JWT de dueño retorna 403 — solo clientes pueden usar este endpoint")
    void duenoRetorna403() {
        webTestClient.post()
                .uri("/api/v1/asistencias/app")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(COMPANIA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("id_sucursal", SUCURSAL))
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── TC-APP-VALID-001 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Cuerpo vacío retorna 400 — id_sucursal es obligatorio")
    void cuerpoVacioRetorna400() {
        Integer idCliente = insertarClienteCore(COMPANIA, SUCURSAL);

        webTestClient.post()
                .uri("/api/v1/asistencias/app")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtCliente(idCliente, COMPANIA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of())
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ── TC-APP-VALID-002 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("id_sucursal nulo retorna 400")
    void idSucursalNuloRetorna400() {
        Integer idCliente = insertarClienteCore(COMPANIA, SUCURSAL);

        webTestClient.post()
                .uri("/api/v1/asistencias/app")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtCliente(idCliente, COMPANIA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("nombre_sucursal", "Sucursal Test"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ── TC-APP-HAPPY-001 — requiere Core Service activo ───────────────────────

    @Test
    @Disabled("Requiere Core Service activo en localhost:8082 (validar-acceso). "
            + "Sin él, CoreServiceClient falla y el endpoint responde 500. "
            + "Habilitar cuando core-service esté levantado en el entorno de pruebas.")
    @DisplayName("Cliente con membresía activa retorna 201 con campos de asistencia")
    void registroExitosoRetorna201() {
        Integer idCliente = insertarClienteCore(COMPANIA, SUCURSAL);
        insertarMembresia(idCliente, COMPANIA);

        Map<String, Object> body = Map.of(
                "id_sucursal", SUCURSAL,
                "nombre_sucursal", "Sucursal Test"
        );

        webTestClient.post()
                .uri("/api/v1/asistencias/app")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtCliente(idCliente, COMPANIA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id_asistencia").exists()
                .jsonPath("$.fecha").isEqualTo(LocalDate.now().toString())
                .jsonPath("$.hora_entrada").isNotEmpty()
                .jsonPath("$.sucursal").isEqualTo("Sucursal Test")
                .jsonPath("$.modo_control").isNotEmpty()
                .jsonPath("$.fecha_fin").isNotEmpty();
    }

    // ── TC-APP-CONFLICT-001 — requiere Core Service activo ────────────────────

    @Test
    @Disabled("Requiere Core Service activo en localhost:8082 (validar-acceso). "
            + "Sin él, CoreServiceClient falla y el endpoint responde 500 en vez de 409. "
            + "Habilitar cuando core-service esté levantado en el entorno de pruebas.")
    @DisplayName("Segundo check-in el mismo día retorna 409")
    void duplicadoMismoDiaRetorna409() {
        Integer idCliente = insertarClienteCore(COMPANIA, SUCURSAL);
        Integer idMembresia = insertarMembresia(idCliente, COMPANIA);

        // Primera asistencia insertada directamente en BD (método válido para el helper)
        insertarAsistencia(COMPANIA, SUCURSAL, idCliente, idMembresia,
                LocalDate.now().toString(), "07:00:00", "qr_cliente");

        Map<String, Object> body = Map.of(
                "id_sucursal", SUCURSAL,
                "nombre_sucursal", "Sucursal Test"
        );

        webTestClient.post()
                .uri("/api/v1/asistencias/app")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtCliente(idCliente, COMPANIA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isEqualTo(409);
    }
}
