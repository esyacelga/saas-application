package com.gymadmin.platform.integration;

import com.gymadmin.platform.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;

@DisplayName("Suscripciones — happy path")
class SuscripcionIntegrationTest extends BaseIntegrationTest {

    // ── TC-SUSC-001 (estado inicial activo) ──────────────────────────────────

    @Test
    @DisplayName("GET /companias/{id}/suscripcion devuelve plan activo recién creado")
    void getSuscripcionActiva() {
        Long idCompania = crearGymConPlan("Plan Test", 29.99, "1797777777001");

        webTestClient.get()
                .uri("/api/v1/companias/{id}/suscripcion", idCompania)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.estado").isEqualTo("ACTIVO")
                .jsonPath("$.tipoCambio").isEqualTo("NUEVO")
                .jsonPath("$.idPlan").isNumber();
    }

    // ── TC-SUSC-008 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /companias/{id}/suscripcion/historial devuelve todo el historial")
    void getHistorialSuscripcion() {
        Long idCompania = crearGymConPlan("Plan Historial", 29.99, "1798888888001");

        webTestClient.get()
                .uri("/api/v1/companias/{id}/suscripcion/historial", idCompania)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)   // la fila inicial tipo=NUEVO
                .jsonPath("$[0].tipoCambio").isEqualTo("NUEVO");
    }

    // ── TC-SUSC-003 (renovar) ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /companias/{id}/suscripcion/renovar crea nueva fila con tipo RENOVACION")
    void renovarSuscripcion() {
        Integer planId = crearPlan("Basico", 29.99);
        Long idCompania = crearGymConPlanId("1799999999001", planId.longValue());

        Map<String, Object> body = Map.of("idPlan", planId, "meses", 1);

        webTestClient.post()
                .uri("/api/v1/companias/{id}/suscripcion/renovar", idCompania)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.tipoCambio").isEqualTo("RENOVACION")
                .jsonPath("$.estado").isEqualTo("ACTIVO");

        // El historial ahora debe tener 2 filas
        webTestClient.get()
                .uri("/api/v1/companias/{id}/suscripcion/historial", idCompania)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2);
    }

    // ── TC-SUSC-004 (upgrade) ────────────────────────────────────────────────

    @Test
    @DisplayName("POST /companias/{id}/suscripcion/upgrade cancela plan actual y activa el nuevo")
    void upgradeplan() {
        Integer planBasico = crearPlan("Basico Upgrade", 29.99);
        Integer planPremium = crearPlan("Premium Upgrade", 59.99);
        Long idCompania = crearGymConPlanId("1700000000001", planBasico.longValue());

        Map<String, Object> body = Map.of("idPlanNuevo", planPremium);

        webTestClient.post()
                .uri("/api/v1/companias/{id}/suscripcion/upgrade", idCompania)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.idCompaniaPlanNuevo").isNumber()
                .jsonPath("$.planAnteriorCancelado").isEqualTo(true)
                .jsonPath("$.montoAPagar").isNumber();

        // La suscripción activa ahora debe ser el plan premium
        webTestClient.get()
                .uri("/api/v1/companias/{id}/suscripcion", idCompania)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.estado").isEqualTo("ACTIVO")
                .jsonPath("$.tipoCambio").isEqualTo("UPGRADE");
    }

    // ── TC-SUSC-006 (downgrade) ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /companias/{id}/suscripcion/downgrade crea plan en estado PROGRAMADO")
    void downgrade() {
        Integer planPremium = crearPlan("Premium Down", 59.99);
        Integer planBasico = crearPlan("Basico Down", 29.99);
        Long idCompania = crearGymConPlanId("1701111111001", planPremium.longValue());

        Map<String, Object> body = Map.of("idPlanNuevo", planBasico);

        webTestClient.post()
                .uri("/api/v1/companias/{id}/suscripcion/downgrade", idCompania)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.idCompaniaPlanNuevo").isNumber()
                .jsonPath("$.estado").isEqualTo("PROGRAMADO")
                .jsonPath("$.efectivoDe").isNotEmpty();

        // Plan actual debe seguir ACTIVO
        webTestClient.get()
                .uri("/api/v1/companias/{id}/suscripcion", idCompania)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.estado").isEqualTo("ACTIVO")
                .jsonPath("$.tipoCambio").isEqualTo("NUEVO");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

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

    Long crearGymConPlan(String planNombre, double precio, String ruc) {
        Integer planId = crearPlan(planNombre, precio);
        return crearGymConPlanId(ruc, planId.longValue());
    }

    Long crearGymConPlanId(String ruc, Long planId) {
        return Long.valueOf(webTestClient.post()
                .uri("/api/v1/companias")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "nombre", "Gym Suscripcion Test",
                        "ruc", ruc,
                        "correo", "suscripcion@test.com",
                        "idPlan", planId,
                        "nombreSucursal", "Sede Principal",
                        "direccionSucursal", "Av. Test 1"
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
