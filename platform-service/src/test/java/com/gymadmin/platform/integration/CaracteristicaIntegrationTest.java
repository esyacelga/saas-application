package com.gymadmin.platform.integration;

import com.gymadmin.platform.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;

@DisplayName("Características — happy path")
class CaracteristicaIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("POST /caracteristicas crea una nueva característica con código único")
    void crearCaracteristica() {
        Map<String, Object> body = Map.of(
                "codigo", "clientes",
                "nombre", "Gestión de clientes",
                "modulo", "clientes"
        );

        webTestClient.post()
                .uri("/api/v1/caracteristicas")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isNumber()
                .jsonPath("$.codigo").isEqualTo("clientes")
                .jsonPath("$.nombre").isEqualTo("Gestión de clientes")
                .jsonPath("$.modulo").isEqualTo("clientes")
                .jsonPath("$.activo").isEqualTo(true);
    }

    @Test
    @DisplayName("GET /caracteristicas devuelve todas las características registradas")
    void listarCaracteristicas() {
        // Crear dos características primero
        crearCarac("membresias", "Membresías", "membresias");
        crearCarac("finanzas", "Módulo de finanzas", "finanzas");

        webTestClient.get()
                .uri("/api/v1/caracteristicas")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2);
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private void crearCarac(String codigo, String nombre, String modulo) {
        webTestClient.post()
                .uri("/api/v1/caracteristicas")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("codigo", codigo, "nombre", nombre, "modulo", modulo))
                .exchange()
                .expectStatus().isCreated();
    }
}
