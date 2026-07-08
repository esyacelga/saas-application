package com.gymadmin.platform.integration;

import com.gymadmin.platform.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

@DisplayName("Sucursales — happy path")
class SucursalIntegrationTest extends BaseIntegrationTest {

    // ── TC-SUC-001 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /companias/{id}/sucursales crea sucursal con qr_token único")
    void crearSucursalGeneraQrToken() {
        Long idCompania = registrarGymConPlan();

        Map<String, Object> body = Map.of(
                "nombre", "Sede Norte",
                "direccion", "Av. Brasil y Gaspar de Villarroel",
                "esPrincipal", false
        );

        webTestClient.post()
                .uri("/api/v1/companias/{id}/sucursales", idCompania)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isNumber()
                .jsonPath("$.nombre").isEqualTo("Sede Norte")
                .jsonPath("$.qrToken").isNotEmpty()
                .jsonPath("$.esPrincipal").isEqualTo(false);
    }

    // ── TC-SUC-002 / TC-SUC-003 ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /sucursales/{id}/qr/renovar genera nuevo token (con expiración)")
    void renovarQrTokenConExpiracion() {
        Long idCompania = registrarGymConPlan();
        Long idSucursal = getPrimeraSucursalId(idCompania);

        webTestClient.post()
                .uri("/api/v1/sucursales/{id}/qr/renovar", idSucursal)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("expiresInHours", 720))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.qrToken").isNotEmpty()
                .jsonPath("$.qrTokenExpira").isNotEmpty();
    }

    @Test
    @DisplayName("POST /sucursales/{id}/qr/renovar sin expiración genera token sin fecha límite")
    void renovarQrTokenSinExpiracion() {
        Long idCompania = registrarGymConPlan();
        Long idSucursal = getPrimeraSucursalId(idCompania);

        webTestClient.post()
                .uri("/api/v1/sucursales/{id}/qr/renovar", idSucursal)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.qrToken").isNotEmpty()
                .jsonPath("$.qrTokenExpira").doesNotExist();
    }

    @Test
    @DisplayName("GET /companias/{id}/sucursales lista las sucursales de la compañía")
    void listarSucursales() {
        Long idCompania = registrarGymConPlan();

        webTestClient.post()
                .uri("/api/v1/companias/{id}/sucursales", idCompania)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("nombre", "Sede Sur", "direccion", "Av. Sur 456", "esPrincipal", false))
                .exchange()
                .expectStatus().isCreated();

        // Debe haber 2: la principal (creada al registrar) + la nueva
        webTestClient.get()
                .uri("/api/v1/companias/{id}/sucursales", idCompania)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2);
    }

    @Test
    @DisplayName("PUT /sucursales/{id} actualiza nombre y dirección")
    void actualizarSucursal() {
        Long idCompania = registrarGymConPlan();
        Long idSucursal = getPrimeraSucursalId(idCompania);

        Map<String, Object> body = Map.of(
                "nombre", "Sede Principal Actualizada",
                "direccion", "Nueva Dirección 789"
        );

        webTestClient.put()
                .uri("/api/v1/sucursales/{id}", idSucursal)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.nombre").isEqualTo("Sede Principal Actualizada")
                .jsonPath("$.direccion").isEqualTo("Nueva Dirección 789");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    Long registrarGymConPlan() {
        Integer planId = (Integer) webTestClient.post()
                .uri("/api/v1/planes")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("nombre", "Premium", "descripcion", "Plan premium", "precioMensual", 59.99))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody()
                .get("id");

        return Long.valueOf(webTestClient.post()
                .uri("/api/v1/companias")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "nombre", "Gym Sucursal Test",
                        "ruc", "1796666666001",
                        "correo", "sucursal@test.com",
                        "idPlan", planId,
                        "nombreSucursal", "Sede Principal",
                        "direccionSucursal", "Av. Amazonas 123"
                ))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody()
                .get("idCompania")
                .toString());
    }

    /** Obtiene el id de la primera sucursal de la compañía usando jsonPath para evitar raw-type casts. */
    Long getPrimeraSucursalId(Long idCompania) {
        List<Map<String, Object>> sucursales = webTestClient.get()
                .uri("/api/v1/companias/{id}/sucursales", idCompania)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .returnResult()
                .getResponseBody();

        return Long.valueOf(sucursales.get(0).get("id").toString());
    }
}
