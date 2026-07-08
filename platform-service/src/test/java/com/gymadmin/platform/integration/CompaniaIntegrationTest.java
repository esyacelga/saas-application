package com.gymadmin.platform.integration;

import com.gymadmin.platform.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;

@DisplayName("Compañías — happy path")
class CompaniaIntegrationTest extends BaseIntegrationTest {

    // ── TC-COMP-001 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /companias registra gym y devuelve ids de compania, plan y sucursal")
    void registrarGymNuevo() {
        Integer planId = crearPlan("Premium", 59.99);

        Map<String, Object> body = Map.of(
                "nombre", "Gym Power Quito",
                "ruc", "1792345678001",
                "telefono", "022345678",
                "whatsapp", "0991234567",
                "correo", "info@gympower.com",
                "idPlan", planId,
                "nombreSucursal", "Sede Principal",
                "direccionSucursal", "Av. Amazonas N35-17"
        );

        webTestClient.post()
                .uri("/api/v1/companias")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.idCompania").isNumber()
                .jsonPath("$.idCompaniaPlan").isNumber()
                .jsonPath("$.idSucursal").isNumber()
                .jsonPath("$.qrToken").isNotEmpty();
    }

    // ── TC-COMP-003 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /companias crea sucursal principal con qr_token generado automáticamente")
    void registrarGymCreaQrToken() {
        Integer planId = crearPlan("Premium", 59.99);
        Map registro = registrarGym("Gym QR Test", "1792222222001", planId);
        Long idCompania = Long.valueOf(registro.get("idCompania").toString());

        webTestClient.get()
                .uri("/api/v1/companias/{id}/sucursales", idCompania)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].qrToken").isNotEmpty()
                .jsonPath("$[0].esPrincipal").isEqualTo(true);
    }

    // ── TC-COMP-004 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /companias crea 3 configuraciones de notificación por defecto (7d, 3d, 1d)")
    void registrarGymCreaNotifConfig() {
        Integer planId = crearPlan("Premium", 59.99);
        Map registro = registrarGym("Gym Notif Test", "1793333333001", planId);
        Long idCompania = Long.valueOf(registro.get("idCompania").toString());

        webTestClient.get()
                .uri("/api/v1/companias/{id}/notif-config", idCompania)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(3);
    }

    // ── TC-COMP-005 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /companias/{id} — super_admin actualiza datos de la compañía")
    void actualizarCompania() {
        Integer planId = crearPlan("Premium", 59.99);
        Map registro = registrarGym("Gym Editable", "1794444444001", planId);
        Long idCompania = Long.valueOf(registro.get("idCompania").toString());

        Map<String, Object> body = Map.of(
                "nombre", "Gym Editable Norte",
                "telefono", "022999999",
                "whatsapp", "0998888888",
                "correo", "norte@gymeditable.com"
        );

        webTestClient.put()
                .uri("/api/v1/companias/{id}", idCompania)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.nombre").isEqualTo("Gym Editable Norte")
                .jsonPath("$.telefono").isEqualTo("022999999");
    }

    // ── TC-COMP-007 ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /companias/{id}/suspender — super_admin suspende compañía activa")
    void suspenderCompania() {
        Integer planId = crearPlan("Premium", 59.99);
        Map registro = registrarGym("Gym Suspendible", "1795555555001", planId);
        Long idCompania = Long.valueOf(registro.get("idCompania").toString());

        Map<String, Object> body = Map.of("motivo", "Falta de pago por 30 días");

        webTestClient.put()
                .uri("/api/v1/companias/{id}/suspender", idCompania)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isNoContent();

        // Verificar que la suscripción quedó suspendida
        webTestClient.get()
                .uri("/api/v1/companias/{id}/suscripcion", idCompania)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.estado").isEqualTo("SUSPENDIDO");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    Integer crearPlan(String nombre, double precio) {
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

    Map registrarGym(String nombre, String ruc, Integer planId) {
        return webTestClient.post()
                .uri("/api/v1/companias")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "nombre", nombre,
                        "ruc", ruc,
                        "correo", "test@test.com",
                        "idPlan", planId,
                        "nombreSucursal", "Sede Principal",
                        "direccionSucursal", "Av. Principal 123"
                ))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
    }
}
