package com.gymadmin.platform.integration;

import com.gymadmin.platform.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

@DisplayName("Módulo Check — happy path")
class ModuloCheckIntegrationTest extends BaseIntegrationTest {

    // ── TC-MW-001 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /modulos/check — plan activo con módulo solicitado devuelve 200 permitido=true")
    void planActivoConModulo() {
        Integer caracId = crearCaracteristica("finanzas", "Finanzas", "finanzas");
        Integer planId = crearPlanConCaracteristica("Premium MW", 59.99, caracId);
        Long idCompania = crearGymConPlanId("1704444444001", planId.longValue());

        webTestClient.get()
                .uri("/api/v1/modulos/check?id_compania={idC}&codigo={cod}", idCompania, "finanzas")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.permitido").isEqualTo(true)
                .jsonPath("$.plan").isEqualTo("Premium MW");
    }

    // ── TC-MW-002 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /modulos/check — plan activo sin módulo solicitado devuelve 403 modulo_no_incluido")
    void planActivoSinModulo() {
        Integer planId = crearPlan("Basico MW", 29.99);
        Long idCompania = crearGymConPlanId("1705555555001", planId.longValue());

        webTestClient.get()
                .uri("/api/v1/modulos/check?id_compania={idC}&codigo={cod}", idCompania, "inventario")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.permitido").isEqualTo(false)
                .jsonPath("$.razon").isEqualTo("modulo_no_incluido");
    }

    // ── TC-MW-003 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /modulos/check — compañía sin plan activo devuelve 402 plan_vencido")
    void sinPlanActivo() {
        // Compañía inexistente (id que no existe en la BD limpia)
        webTestClient.get()
                .uri("/api/v1/modulos/check?id_compania=99999&codigo=finanzas")
                .exchange()
                .expectStatus().isEqualTo(402)
                .expectBody()
                .jsonPath("$.permitido").isEqualTo(false)
                .jsonPath("$.razon").isEqualTo("plan_vencido");
    }

    // ── TC-MW-004 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /modulos/check — plan en_gracia sigue permitiendo acceso")
    void planEnGraciaPermiteAcceso() {
        Integer caracId = crearCaracteristica("clientes", "Clientes", "clientes");
        Integer planId = crearPlanConCaracteristica("Basico Gracia", 29.99, caracId);
        Long idCompania = crearGymConPlanId("1706666666001", planId.longValue());

        // Forzar el estado a en_gracia directamente en BD
        databaseClient.sql(
                "UPDATE tenant.compania_planes SET estado = 'en_gracia', fecha_fin = CURRENT_DATE - 1 " +
                "WHERE id_compania = :id AND estado = 'activo'"
        ).bind("id", idCompania).then().block();

        webTestClient.get()
                .uri("/api/v1/modulos/check?id_compania={idC}&codigo={cod}", idCompania, "clientes")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.permitido").isEqualTo(true);
    }

    // ── TC-MW-005 ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /modulos/check — plan suspendido bloquea todo acceso con 402 plan_suspendido")
    void planSuspendidoBloqueaAcceso() {
        Integer caracId = crearCaracteristica("marketing", "Marketing", "marketing");
        Integer planId = crearPlanConCaracteristica("Premium Susp", 59.99, caracId);
        Long idCompania = crearGymConPlanId("1707777777001", planId.longValue());

        // Suspender vía API
        webTestClient.put()
                .uri("/api/v1/companias/{id}/suspender", idCompania)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("motivo", "Prueba de suspensión"))
                .exchange()
                .expectStatus().isNoContent();

        webTestClient.get()
                .uri("/api/v1/modulos/check?id_compania={idC}&codigo={cod}", idCompania, "marketing")
                .exchange()
                .expectStatus().isEqualTo(402)
                .expectBody()
                .jsonPath("$.permitido").isEqualTo(false)
                .jsonPath("$.razon").isEqualTo("plan_suspendido");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    Integer crearCaracteristica(String codigo, String nombre, String modulo) {
        return (Integer) webTestClient.post()
                .uri("/api/v1/caracteristicas")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("codigo", codigo, "nombre", nombre, "modulo", modulo))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody()
                .get("id");
    }

    Integer crearPlan(String nombre, double precio) {
        return (Integer) webTestClient.post()
                .uri("/api/v1/planes")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("nombre", nombre, "descripcion", "desc", "precioMensual", precio))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody()
                .get("id");
    }

    Integer crearPlanConCaracteristica(String nombre, double precio, Integer caracId) {
        Integer planId = crearPlan(nombre, precio);
        webTestClient.put()
                .uri("/api/v1/planes/{id}/caracteristicas", planId)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("caracteristicaIds", List.of(caracId)))
                .exchange()
                .expectStatus().isOk();
        return planId;
    }

    Long crearGymConPlanId(String ruc, Long planId) {
        return Long.valueOf(webTestClient.post()
                .uri("/api/v1/companias")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "nombre", "Gym Modulo Test",
                        "ruc", ruc,
                        "correo", "modulo@test.com",
                        "idPlan", planId,
                        "nombreSucursal", "Sede MW",
                        "direccionSucursal", "Calle MW 1"
                ))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody()
                .get("idCompania")
                .toString());
    }
}
