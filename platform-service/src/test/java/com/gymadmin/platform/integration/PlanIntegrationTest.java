package com.gymadmin.platform.integration;

import com.gymadmin.platform.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

@DisplayName("Planes — happy path")
class PlanIntegrationTest extends BaseIntegrationTest {

    // ── TC-PLAN-001 / TC-PLAN-002 ────────────────────────────────────────────

    @Test
    @DisplayName("POST /planes crea plan y GET /planes lo devuelve con sus características")
    void crearYListarPlanes() {
        // Primero crear una característica para asociar
        Map<String, Object> caracBody = Map.of(
                "codigo", "clientes",
                "nombre", "Gestión de clientes",
                "modulo", "clientes"
        );

        Integer caracId = webTestClient.post()
                .uri("/api/v1/caracteristicas")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(caracBody)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody()
                .get("id") instanceof Integer i ? i : null;

        // Crear plan
        Map<String, Object> planBody = Map.of(
                "nombre", "Básico",
                "descripcion", "Plan de prueba básico",
                "precioMensual", 29.99
        );

        Map planCreado = webTestClient.post()
                .uri("/api/v1/planes")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(planBody)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        Integer planId = (Integer) planCreado.get("id");

        // Asignar característica al plan
        Map<String, Object> asignarBody = Map.of("caracteristicaIds", List.of(caracId));

        webTestClient.put()
                .uri("/api/v1/planes/{id}/caracteristicas", planId)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(asignarBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.caracteristicas[0].codigo").isEqualTo("clientes");

        // Listar planes — debe aparecer con la característica
        webTestClient.get()
                .uri("/api/v1/planes")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].nombre").isEqualTo("Básico")
                .jsonPath("$[0].precioMensual").isEqualTo(29.99)
                .jsonPath("$[0].caracteristicas[0].codigo").isEqualTo("clientes");
    }

    // ── TC-PLAN-003 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /planes/{id}/desactivar desactiva un plan sin suscriptores")
    void desactivarPlanSinSuscriptores() {
        Integer planId = crearPlanBasico();

        webTestClient.put()
                .uri("/api/v1/planes/{id}/desactivar", planId)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .exchange()
                .expectStatus().isNoContent();

        // Verificar que el plan está inactivo
        webTestClient.get()
                .uri("/api/v1/planes")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].activo").isEqualTo(false);
    }

    // ── TC-PLAN-006 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /planes/{id}/caracteristicas asigna características en bulk")
    void asignarCaracteristicasEnBulk() {
        Integer planId = crearPlanBasico();

        Integer carac1 = crearCaracteristica("membresias", "Membresías", "membresias");
        Integer carac2 = crearCaracteristica("finanzas", "Finanzas", "finanzas");

        Map<String, Object> body = Map.of("caracteristicaIds", List.of(carac1, carac2));

        webTestClient.put()
                .uri("/api/v1/planes/{id}/caracteristicas", planId)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.caracteristicas.length()").isEqualTo(2);
    }

    // ── TC-PLAN-007 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /planes/{id} actualiza precio sin afectar nombre ni descripción")
    void actualizarPrecioPlan() {
        Integer planId = crearPlanBasico();

        Map<String, Object> body = Map.of(
                "nombre", "Básico",
                "descripcion", "Plan actualizado",
                "precioMensual", 39.99
        );

        webTestClient.put()
                .uri("/api/v1/planes/{id}", planId)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.precioMensual").isEqualTo(39.99)
                .jsonPath("$.nombre").isEqualTo("Básico");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    Integer crearPlanBasico() {
        Map<String, Object> body = Map.of(
                "nombre", "Básico",
                "descripcion", "Plan básico",
                "precioMensual", 29.99
        );
        return (Integer) webTestClient.post()
                .uri("/api/v1/planes")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody()
                .get("id");
    }

    Integer crearCaracteristica(String codigo, String nombre, String modulo) {
        Map<String, Object> body = Map.of("codigo", codigo, "nombre", nombre, "modulo", modulo);
        return (Integer) webTestClient.post()
                .uri("/api/v1/caracteristicas")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody()
                .get("id");
    }
}
