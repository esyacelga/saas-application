package com.gymadmin.platform.integration;

import com.gymadmin.platform.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.Map;

@DisplayName("Pagos — happy path")
class PagoIntegrationTest extends BaseIntegrationTest {

    // ── TC-PAY-001 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /pagos registra pago en estado PENDIENTE")
    void registrarPago() {
        Map<String, Long> gymIds = crearGymConPlan();
        Long idCompaniaPlan = gymIds.get("idCompaniaPlan");

        Map<String, Object> body = Map.of(
                "idCompaniaPlan", idCompaniaPlan,
                "monto", 59.99,
                "metodoPago", "transferencia",
                "tipoPago", "pago_completo",
                "referencia", "TXN-TEST-001",
                "periodoDesde", LocalDate.now().toString(),
                "periodoHasta", LocalDate.now().plusMonths(1).toString()
        );

        webTestClient.post()
                .uri("/api/v1/pagos")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isNumber()
                .jsonPath("$.estado").isEqualTo("PENDIENTE")
                .jsonPath("$.monto").isEqualTo(59.99)
                .jsonPath("$.referencia").isEqualTo("TXN-TEST-001");
    }

    // ── TC-PAY-002 ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /pagos/{id}/confirmar cambia estado a PAGADO")
    void confirmarPago() {
        Map<String, Long> gymIds = crearGymConPlan();
        Long idCompaniaPlan = gymIds.get("idCompaniaPlan");

        // Registrar pago primero
        Map<String, Object> pagoBody = Map.of(
                "idCompaniaPlan", idCompaniaPlan,
                "monto", 59.99,
                "metodoPago", "efectivo",
                "tipoPago", "pago_completo",
                "referencia", "TXN-TEST-002",
                "periodoDesde", LocalDate.now().toString(),
                "periodoHasta", LocalDate.now().plusMonths(1).toString()
        );

        Integer pagoId = (Integer) webTestClient.post()
                .uri("/api/v1/pagos")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(pagoBody)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody()
                .get("id");

        // Confirmar pago
        webTestClient.put()
                .uri("/api/v1/pagos/{id}/confirmar", pagoId)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.estado").isEqualTo("PAGADO");
    }

    @Test
    @DisplayName("GET /companias/{id}/pagos devuelve historial de pagos de la compañía")
    void historialPagos() {
        Map<String, Long> gymIds = crearGymConPlan();
        Long idCompania = gymIds.get("idCompania");
        Long idCompaniaPlan = gymIds.get("idCompaniaPlan");

        // Registrar dos pagos
        for (int i = 1; i <= 2; i++) {
            webTestClient.post()
                    .uri("/api/v1/pagos")
                    .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of(
                            "idCompaniaPlan", idCompaniaPlan,
                            "monto", 29.99,
                            "metodoPago", "tarjeta",
                            "tipoPago", "pago_completo",
                            "referencia", "TXN-TEST-00" + i
                    ))
                    .exchange()
                    .expectStatus().isCreated();
        }

        webTestClient.get()
                .uri("/api/v1/companias/{id}/pagos", idCompania)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(2);
    }

    // ── helper ────────────────────────────────────────────────────────────────

    Map<String, Long> crearGymConPlan() {
        Integer planId = (Integer) webTestClient.post()
                .uri("/api/v1/planes")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("nombre", "Premium Pago", "descripcion", "Plan pago test", "precioMensual", 59.99))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody()
                .get("id");

        Map registro = webTestClient.post()
                .uri("/api/v1/companias")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "nombre", "Gym Pago Test",
                        "ruc", "1702222222001",
                        "correo", "pago@test.com",
                        "idPlan", planId,
                        "nombreSucursal", "Sede Pago",
                        "direccionSucursal", "Calle Pago 1"
                ))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        return Map.of(
                "idCompania", Long.valueOf(registro.get("idCompania").toString()),
                "idCompaniaPlan", Long.valueOf(registro.get("idCompaniaPlan").toString())
        );
    }
}
