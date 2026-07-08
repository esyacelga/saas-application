package com.gymadmin.core.integration;

import com.gymadmin.core.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

@DisplayName("Tipos de Membresía — integración")
class TipoMembresiaIntegrationTest extends BaseIntegrationTest {

    // ── GET /tipos-membresia ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /tipos-membresia devuelve lista de tipos activos de la compañía")
    void listarTiposActivos() {
        seedTipoCalendario("Mensual Test");

        get("/api/v1/tipos-membresia", jwtAdminCompania(TEST_COMPANIA))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$[0].nombre").isEqualTo("Mensual Test")
                .jsonPath("$[0].modo_control").isEqualTo("calendario")
                .jsonPath("$[0].activo").isEqualTo(true);
    }

    // ── POST /tipos-membresia ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /tipos-membresia crea tipo calendario y devuelve 201")
    void crearTipoCalendario() {
        post("/api/v1/tipos-membresia", jwtAdminCompania(TEST_COMPANIA), Map.of(
                "nombre", "Mensual",
                "modo_control", "calendario",
                "duracion_tipo", "meses",
                "duracion_valor", 1,
                "precio", 35.00
        ))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isNumber()
                .jsonPath("$.nombre").isEqualTo("Mensual")
                .jsonPath("$.modo_control").isEqualTo("calendario")
                .jsonPath("$.precio").isEqualTo(35.0)
                .jsonPath("$.activo").isEqualTo(true);
    }

    @Test
    @DisplayName("POST /tipos-membresia crea tipo accesos con dias_acceso y devuelve 201")
    void crearTipoAccesos() {
        post("/api/v1/tipos-membresia", jwtAdminCompania(TEST_COMPANIA), Map.of(
                "nombre", "Tarjeta 22",
                "modo_control", "accesos",
                "duracion_tipo", "meses",
                "duracion_valor", 3,
                "dias_acceso", 22,
                "precio", 35.00
        ))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.modo_control").isEqualTo("accesos")
                .jsonPath("$.dias_acceso").isEqualTo(22);
    }

    // ── PUT /tipos-membresia/{id} ────────────────────────────────────────────

    @Test
    @DisplayName("PUT /tipos-membresia/{id} actualiza precio y devuelve 200")
    void actualizarPrecio() {
        Long id = seedTipoCalendario("Trimestral");

        put("/api/v1/tipos-membresia/" + id, jwtAdminCompania(TEST_COMPANIA), Map.of(
                "nombre", "Trimestral",
                "modo_control", "calendario",
                "duracion_tipo", "meses",
                "duracion_valor", 3,
                "precio", 90.00
        ))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.precio").isEqualTo(90.0);
    }

    // ── PUT /tipos-membresia/{id}/desactivar ─────────────────────────────────

    @Test
    @DisplayName("PUT /tipos-membresia/{id}/desactivar desactiva tipo sin membresías activas")
    void desactivarTipoSinMembresias() {
        Long id = seedTipoCalendario("Para Desactivar");

        put("/api/v1/tipos-membresia/" + id + "/desactivar", jwtAdminCompania(TEST_COMPANIA))
                .expectStatus().isOk();
    }

    // ── error cases ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /tipos-membresia/{id}/desactivar devuelve 409 si hay membresías activas del tipo")
    void desactivarTipoConMembresiasActivas() {
        Long idTipo = seedTipoCalendario("Tipo Con Activas");
        Long idPersona = seedPersona("TEST-TIPO-001", "Mario Ríos");
        Long idCliente = seedCliente(idPersona);
        seedMembresia(idCliente, idTipo,
                java.time.LocalDate.now().toString(),
                java.time.LocalDate.now().plusMonths(1).toString());

        put("/api/v1/tipos-membresia/" + idTipo + "/desactivar", jwtAdminCompania(TEST_COMPANIA))
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.message").isEqualTo("No se puede desactivar: existen membresías activas de este tipo");
    }
}
