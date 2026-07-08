package com.gymadmin.platform.integration;

import com.gymadmin.platform.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;

@DisplayName("Wizard — registro completo de gym")
class WizardIntegrationTest extends BaseIntegrationTest {

    // ── TC-WIZ-001 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /companias/wizard registra gym con usuario principal y devuelve ids + idPersona")
    void wizardRegistraGymConUsuarioPrincipal() {
        Long idPersona = crearPersona("IT-WIZ-001", "Admin Wizard Test");
        Integer planId = crearPlan("Premium Wizard", 59.99);

        Map<String, Object> body = Map.of(
                "nombre", "Gym Wizard Test",
                "ruc", "1799000001001",
                "correo", "admin@gymwizard.com",
                "telefono", "022111111",
                "idPlan", planId,
                "nombreSucursal", "Sede Principal Wizard",
                "direccionSucursal", "Av. Wizard 123",
                "usuarioPrincipal", Map.of(
                        "idPersona", idPersona,
                        "correo", "admin@gymwizard.com",
                        "password", "Password123"
                )
        );

        webTestClient.post()
                .uri("/api/v1/companias/wizard")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.idCompania").isNumber()
                .jsonPath("$.idCompaniaPlan").isNumber()
                .jsonPath("$.idSucursal").isNumber()
                .jsonPath("$.qrToken").isNotEmpty()
                .jsonPath("$.usuarioPrincipal.id").isNumber()
                .jsonPath("$.usuarioPrincipal.idPersona").isEqualTo(idPersona.intValue())
                .jsonPath("$.usuarioPrincipal.correo").isEqualTo("admin@gymwizard.com")
                .jsonPath("$.usuariosCreados").isEqualTo(1);
    }

    // ── TC-WIZ-002 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /companias/wizard con usuarios adicionales crea todos los usuarios")
    void wizardRegistraGymConUsuariosAdicionales() {
        Long idPersonaPrincipal = crearPersona("IT-WIZ-002A", "Admin Wizard Adicionales");
        Long idPersonaAdicional = crearPersona("IT-WIZ-002B", "Recepcionista Wizard");
        Integer planId = crearPlan("Premium Wizard Multi", 59.99);

        Map<String, Object> body = Map.of(
                "nombre", "Gym Multi Wizard",
                "ruc", "1799000002001",
                "correo", "multi@gymwizard.com",
                "idPlan", planId,
                "nombreSucursal", "Sede Multi",
                "usuarioPrincipal", Map.of(
                        "idPersona", idPersonaPrincipal,
                        "correo", "principal@gymwizard.com",
                        "password", "Password123"
                ),
                "usuariosAdicionales", java.util.List.of(
                        Map.of(
                                "idPersona", idPersonaAdicional,
                                "correo", "recep@gymwizard.com",
                                "password", "Password456"
                        )
                )
        );

        webTestClient.post()
                .uri("/api/v1/companias/wizard")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.usuariosCreados").isEqualTo(2);
    }

    // ── TC-WIZ-003 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /companias/wizard devuelve 400 cuando falta idPersona en usuarioPrincipal")
    void wizardDevuelve400SinIdPersona() {
        Integer planId = crearPlan("Plan Validacion", 29.99);

        Map<String, Object> body = Map.of(
                "nombre", "Gym Sin Persona",
                "ruc", "1799000003001",
                "idPlan", planId,
                "nombreSucursal", "Sede Test",
                "usuarioPrincipal", Map.of(
                        "correo", "sin@persona.com",
                        "password", "Password123"
                )
        );

        webTestClient.post()
                .uri("/api/v1/companias/wizard")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ── TC-WIZ-004 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /companias/wizard devuelve 403 para token sin super_admin")
    void wizardDevuelve403SinSuperAdmin() {
        Integer planId = crearPlan("Plan Auth Test", 29.99);

        Map<String, Object> body = Map.of(
                "nombre", "Gym Auth Test",
                "ruc", "1799000099001",
                "correo", "auth@test.com",
                "idPlan", planId,
                "nombreSucursal", "Sede Auth",
                "usuarioPrincipal", Map.of(
                        "idPersona", 1L,
                        "correo", "auth@test.com",
                        "password", "Password123"
                )
        );

        webTestClient.post()
                .uri("/api/v1/companias/wizard")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSoporte()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Integer crearPlan(String nombre, double precio) {
        return (Integer) webTestClient.post()
                .uri("/api/v1/planes")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("nombre", nombre, "descripcion", "Plan " + nombre, "precioMensual", precio))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody()
                .get("id");
    }
}
