package com.gymadmin.attendance.integration;

import com.gymadmin.attendance.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;

@DisplayName("Plantillas de mensajes — integración BD + endpoint")
class PlantillaIntegrationTest extends BaseIntegrationTest {

    private static final Integer COMPANIA = 1;
    private static final Integer SUCURSAL = 1;

    // ── TC-PLT-001 — Crear plantilla ──────────────────────────────────────────

    @Test
    @DisplayName("POST /plantillas crea plantilla y GET /plantillas la lista")
    void crearYListarPlantilla() {
        Map<String, Object> body = Map.of(
                "tipo", "ausencia_2d",
                "nombre", "Motivacional suave",
                "contenido", "Hola {nombre}, te extrañamos en {gym_nombre}."
        );

        webTestClient.post()
                .uri("/api/v1/plantillas")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(COMPANIA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isNumber()
                .jsonPath("$.tipo").isEqualTo("ausencia_2d")
                .jsonPath("$.nombre").isEqualTo("Motivacional suave")
                .jsonPath("$.activo").isEqualTo(true);

        webTestClient.get()
                .uri("/api/v1/plantillas")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(COMPANIA)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].tipo").isEqualTo("ausencia_2d");
    }

    // ── TC-PLT-002 — Listar solo del gym propio ───────────────────────────────

    @Test
    @DisplayName("GET /plantillas filtra por id_compania del JWT — no mezcla gyms")
    void listarSoloDelGymPropio() {
        // Gym 1
        insertarPlantilla(1, 1, "ausencia_2d", "Gym1", "Contenido gym 1");
        // Gym 2 (no debe aparecer)
        insertarPlantilla(2, 1, "ausencia_2d", "Gym2", "Contenido gym 2");

        webTestClient.get()
                .uri("/api/v1/plantillas")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(1)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].nombre").isEqualTo("Gym1");
    }

    // ── TC-PLT-003 — Actualizar plantilla ─────────────────────────────────────

    @Test
    @DisplayName("PUT /plantillas/{id} actualiza contenido y activo")
    void actualizarPlantilla() {
        Integer id = insertarPlantilla(COMPANIA, SUCURSAL, "recuperacion_5d",
                "Original", "Texto original {nombre}");

        Map<String, Object> updateBody = Map.of(
                "contenido", "Texto actualizado {nombre} llevas {dias} días",
                "activo", false,
                "nombre", "Actualizado"
        );

        webTestClient.put()
                .uri("/api/v1/plantillas/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(COMPANIA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(updateBody)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.contenido").isEqualTo("Texto actualizado {nombre} llevas {dias} días")
                .jsonPath("$.activo").isEqualTo(false)
                .jsonPath("$.nombre").isEqualTo("Actualizado");
    }

    // ── TC-PLT-004 — Eliminar con 2 activas del tipo ─────────────────────────

    @Test
    @DisplayName("DELETE /plantillas/{id} OK cuando hay 2 activas del mismo tipo")
    void eliminarCuandoHay2Activas() {
        Integer id1 = insertarPlantilla(COMPANIA, SUCURSAL, "ausencia_2d", "P1", "Texto 1");
        insertarPlantilla(COMPANIA, SUCURSAL, "ausencia_2d", "P2", "Texto 2");

        webTestClient.delete()
                .uri("/api/v1/plantillas/{id}", id1)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(COMPANIA)))
                .exchange()
                .expectStatus().isNoContent();

        // Verificar que la otra sigue activa
        webTestClient.get()
                .uri("/api/v1/plantillas")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(COMPANIA)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].nombre").isEqualTo("P2");
    }

    // ── TC-PLT-005 — Guard: no eliminar la única activa ───────────────────────

    @Test
    @DisplayName("DELETE /plantillas/{id} retorna 409 si es la única activa del tipo")
    void noEliminarUnicaActiva() {
        Integer id = insertarPlantilla(COMPANIA, SUCURSAL, "vencimiento_hoy", "Única", "Texto único");

        webTestClient.delete()
                .uri("/api/v1/plantillas/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(COMPANIA)))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("ultima_plantilla");
    }

    // ── TC-PLT-006 — No se puede eliminar plantilla de otro gym ──────────────

    @Test
    @DisplayName("DELETE /plantillas/{id} de otro gym retorna 403")
    void noEliminarPlantillaDeOtroGym() {
        // Plantilla del gym 2
        Integer id = insertarPlantilla(2, 1, "ausencia_2d", "Gym2", "Texto gym 2");

        // JWT del gym 1
        webTestClient.delete()
                .uri("/api/v1/plantillas/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(1)))
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── TC-PLT-007 — Plantilla inexistente retorna 404 ────────────────────────

    @Test
    @DisplayName("PUT /plantillas/{id} con id inexistente retorna 404")
    void plantillaInexistente() {
        webTestClient.put()
                .uri("/api/v1/plantillas/99999")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(COMPANIA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("contenido", "Nuevo"))
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── TC-PLT-008 — Solo Dueño puede crear ──────────────────────────────────

    @Test
    @DisplayName("POST /plantillas con JWT recepción retorna 403")
    void recepcionNoPuedeCrarPlantilla() {
        webTestClient.post()
                .uri("/api/v1/plantillas")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtRecepcion(COMPANIA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("tipo", "ausencia_2d", "nombre", "P", "contenido", "C"))
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── TC-PLT-009 — Validación campos obligatorios ──────────────────────────

    @Test
    @DisplayName("POST /plantillas sin campos obligatorios retorna 400")
    void crearSinCamposRetorna400() {
        webTestClient.post()
                .uri("/api/v1/plantillas")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(COMPANIA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("tipo", "ausencia_2d"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ── TC-PLT-010 — Soft-delete: eliminado=true, no aparece en lista ─────────

    @Test
    @DisplayName("Plantilla eliminada no aparece en GET /plantillas")
    void plantillaEliminadaNoAparece() {
        Integer id1 = insertarPlantilla(COMPANIA, SUCURSAL, "motivacional", "Activa", "Texto activo");
        Integer id2 = insertarPlantilla(COMPANIA, SUCURSAL, "motivacional", "A borrar", "Texto borrar");

        webTestClient.delete()
                .uri("/api/v1/plantillas/{id}", id2)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(COMPANIA)))
                .exchange()
                .expectStatus().isNoContent();

        webTestClient.get()
                .uri("/api/v1/plantillas")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(COMPANIA)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].nombre").isEqualTo("Activa");
    }
}
