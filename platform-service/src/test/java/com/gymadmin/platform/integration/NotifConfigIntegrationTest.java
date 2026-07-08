package com.gymadmin.platform.integration;

import com.gymadmin.platform.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

@DisplayName("Configuración de Notificaciones — happy path")
class NotifConfigIntegrationTest extends BaseIntegrationTest {

    // ── TC-NOTIF-001 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /companias/{id}/notif-config reemplaza toda la configuración anterior")
    void reemplazarConfig() {
        Long idCompania = crearGymConPlan();

        // Configuración inicial (3 filas creadas al registrar el gym)
        webTestClient.get()
                .uri("/api/v1/companias/{id}/notif-config", idCompania)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(3);

        // Reemplazar con 2 entradas
        Map<String, Object> body = Map.of("configs", List.of(
                Map.of("diasAntes", 7, "canal", "whatsapp", "activo", true),
                Map.of("diasAntes", 3, "canal", "ambos", "activo", true)
        ));

        webTestClient.put()
                .uri("/api/v1/companias/{id}/notif-config", idCompania)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isNoContent();

        // Verificar que ahora hay solo 2 entradas
        webTestClient.get()
                .uri("/api/v1/companias/{id}/notif-config", idCompania)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2);
    }

    @Test
    @DisplayName("GET /companias/{id}/notif-config devuelve configuraciones de notificación")
    void getConfig() {
        Long idCompania = crearGymConPlan();

        webTestClient.get()
                .uri("/api/v1/companias/{id}/notif-config", idCompania)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].diasAntes").isNumber()
                .jsonPath("$[0].canal").isNotEmpty()
                .jsonPath("$[0].activo").isEqualTo(true);
    }

    // ── helper ────────────────────────────────────────────────────────────────

    Long crearGymConPlan() {
        Integer planId = (Integer) webTestClient.post()
                .uri("/api/v1/planes")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("nombre", "Plan Notif", "descripcion", "desc", "precioMensual", 39.99))
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
                        "nombre", "Gym Notif Test",
                        "ruc", "1703333333001",
                        "correo", "notif@test.com",
                        "idPlan", planId,
                        "nombreSucursal", "Sede Notif",
                        "direccionSucursal", "Calle Notif 1"
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
