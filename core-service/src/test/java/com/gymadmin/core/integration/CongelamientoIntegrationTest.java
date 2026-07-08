package com.gymadmin.core.integration;

import com.gymadmin.core.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.test.web.reactive.server.WebTestClient;

import java.time.LocalDate;
import java.util.Map;

@DisplayName("Congelamientos — integración")
class CongelamientoIntegrationTest extends BaseIntegrationTest {

    // ── POST /membresias/{id}/congelar ───────────────────────────────────────

    @Test
    @DisplayName("POST /membresias/{id}/congelar congela membresía activa y devuelve 201")
    void congelarMembresia() {
        Long idPersona = seedPersona("TEST-CONG-001", "Patricia Nava");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoCalendario("Mensual Cong");
        Long idMem     = seedMembresia(idCliente, idTipo,
                LocalDate.now().toString(),
                LocalDate.now().plusMonths(1).toString());

        post("/api/v1/membresias/" + idMem + "/congelar",
                jwtRecepcion(TEST_COMPANIA), Map.of(
                        "fecha_inicio", LocalDate.now().toString(),
                        "motivo", "viaje",
                        "detalle", "Viaje de trabajo",
                        "retroactivo", false
                ))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id_congelamiento").isNumber()
                .jsonPath("$.fecha_inicio").isEqualTo(LocalDate.now().toString());
    }

    // ── PUT /congelamientos/{id}/reactivar ───────────────────────────────────

    @Test
    @DisplayName("PUT /congelamientos/{id}/reactivar reactiva y compensa días correctamente")
    void reactivarCongelamiento() {
        Long idPersona = seedPersona("TEST-CONG-002", "Miguel Ávila");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoCalendario("Mensual React");
        Long idMem     = seedMembresia(idCliente, idTipo,
                LocalDate.now().toString(),
                LocalDate.now().plusMonths(1).toString());

        Long idCong = extractIdCongelamiento(
                post("/api/v1/membresias/" + idMem + "/congelar",
                        jwtRecepcion(TEST_COMPANIA), Map.of(
                                "fecha_inicio", LocalDate.now().minusDays(7).toString(),
                                "motivo", "enfermedad",
                                "retroactivo", false
                        ))
        );

        put("/api/v1/congelamientos/" + idCong + "/reactivar", jwtRecepcion(TEST_COMPANIA))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.dias_compensados").isNumber()
                .jsonPath("$.fecha_fin_nueva").isNotEmpty()
                .jsonPath("$.fecha_fin_anterior").isNotEmpty();
    }

    // ── GET /membresias/{id}/congelamientos ──────────────────────────────────

    @Test
    @DisplayName("GET /membresias/{id}/congelamientos devuelve historial de congelamientos")
    void historialCongelamientos() {
        Long idPersona = seedPersona("TEST-CONG-003", "Sandra Ibáñez");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoCalendario("Mensual Hist Cong");
        Long idMem     = seedMembresia(idCliente, idTipo,
                LocalDate.now().toString(),
                LocalDate.now().plusMonths(1).toString());

        Long idCong = extractIdCongelamiento(
                post("/api/v1/membresias/" + idMem + "/congelar",
                        jwtRecepcion(TEST_COMPANIA), Map.of(
                                "fecha_inicio", LocalDate.now().toString(),
                                "motivo", "voluntario",
                                "retroactivo", false
                        ))
        );

        put("/api/v1/congelamientos/" + idCong + "/reactivar", jwtRecepcion(TEST_COMPANIA))
                .expectStatus().isOk();

        get("/api/v1/membresias/" + idMem + "/congelamientos", jwtAdminCompania(TEST_COMPANIA))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$[0].id").isNumber()
                .jsonPath("$[0].motivo").isEqualTo("voluntario");
    }

    // ── error cases ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /membresias/{id}/congelar devuelve 409 si la membresía ya está congelada")
    void congelarMembresiaYaCongelada() {
        Long idPersona = seedPersona("TEST-CONG-004", "Beatriz Flores");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoCalendario("Mensual Ya Cong");
        Long idMem     = seedMembresia(idCliente, idTipo,
                LocalDate.now().toString(),
                LocalDate.now().plusMonths(1).toString());

        Map<String, Object> body = Map.of(
                "fecha_inicio", LocalDate.now().toString(),
                "motivo", "viaje",
                "retroactivo", false
        );

        post("/api/v1/membresias/" + idMem + "/congelar", jwtRecepcion(TEST_COMPANIA), body)
                .expectStatus().isCreated();

        post("/api/v1/membresias/" + idMem + "/congelar", jwtRecepcion(TEST_COMPANIA), body)
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.message").isEqualTo("La membresía ya está congelada");
    }

    @Test
    @DisplayName("POST /membresias/{id}/congelar devuelve 422 si la membresía está anulada")
    void congelarMembresiaAnulada() {
        Long idPersona = seedPersona("TEST-CONG-005", "Jorge Salinas");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoCalendario("Mensual Anulada Cong");
        Long idMem     = seedMembresia(idCliente, idTipo,
                LocalDate.now().toString(),
                LocalDate.now().plusMonths(1).toString());

        put("/api/v1/membresias/" + idMem + "/anular",
                jwtAdminCompania(TEST_COMPANIA), Map.of("motivo", "Test"))
                .expectStatus().isOk();

        post("/api/v1/membresias/" + idMem + "/congelar",
                jwtRecepcion(TEST_COMPANIA), Map.of(
                        "fecha_inicio", LocalDate.now().toString(),
                        "motivo", "viaje",
                        "retroactivo", false
                ))
                .expectStatus().isEqualTo(422)
                .expectBody()
                .jsonPath("$.message").isEqualTo("Solo se pueden congelar membresías activas");
    }

    // ── helper ───────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Long extractIdCongelamiento(WebTestClient.ResponseSpec response) {
        Map<String, Object> body = response
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        return Long.valueOf(body.get("id_congelamiento").toString());
    }
}
