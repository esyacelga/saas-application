package com.gymadmin.platform.integration;

import com.gymadmin.platform.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;

import java.time.LocalDate;
import java.util.Map;

/**
 * REQ-SAAS-001 (Sub-fase 1.6, item #5): idempotencia del endpoint
 * {@code POST /api/v1/companias/{id}/pagos/reportar}.
 *
 * <p>Regla RN-08: {@code hash_idempotencia = SHA-256(id_compania|monto|fecha_transferencia|referencia)}.
 * Un unique index parcial en {@code tenant.pagos_pendientes_validacion.hash_idempotencia}
 * (donde estado IN ('pendiente','aprobado')) refuerza la unicidad a nivel de BD.
 *
 * <p>Comportamiento esperado tras 2 requests con la misma tripleta
 * {@code (idCompania, monto, fecha_transferencia, referencia)}:
 * <ul>
 *   <li>Primero → 201 CREATED con el nuevo pago pendiente.</li>
 *   <li>Segundo → 409 CONFLICT con código {@code pago_duplicado}
 *       ({@link com.gymadmin.platform.domain.exception.PagoDuplicadoException}).</li>
 *   <li>En BD: exactamente 1 fila con esa referencia + tenant.</li>
 * </ul>
 */
@DisplayName("Pagos reportar — idempotencia (Sub-fase 1.6 item #5)")
class PagoReportarIdempotenciaIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("2 requests idénticos → primero 201, segundo 409 pago_duplicado y 1 sola fila en BD")
    void reportarPagoIdempotente() {
        Long idCompania = crearGymConPlan("Gym Idempotencia", "1710000000001");
        Long idPlanDestino = crearPlanPremium("Premium Idempotencia");

        String referencia = "TXN-IDEM-001";
        String fechaTransferencia = LocalDate.now().toString();
        String monto = "59.99";

        // ── Primer reporte → 201 ──────────────────────────────────────────────
        Integer idPrimero = webTestClient.post()
                .uri("/api/v1/companias/{id}/pagos/reportar", idCompania)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtOwnerCompania(idCompania)))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyReporte(idPlanDestino, monto, fechaTransferencia, referencia)))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody()
                .get("id") instanceof Integer i ? i : null;

        // ── Segundo reporte con misma tripleta → 409 pago_duplicado ──────────
        webTestClient.post()
                .uri("/api/v1/companias/{id}/pagos/reportar", idCompania)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtOwnerCompania(idCompania)))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyReporte(idPlanDestino, monto, fechaTransferencia, referencia)))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.codigo").isEqualTo("pago_duplicado");

        // ── Verificación de BD: exactamente 1 fila con esa referencia + tenant ─
        Long cantidad = databaseClient.sql(
                "SELECT COUNT(*) AS c FROM tenant.pagos_pendientes_validacion " +
                "WHERE id_compania = :id AND referencia = :ref")
                .bind("id", idCompania)
                .bind("ref", referencia)
                .map((row, meta) -> {
                    Number n = row.get("c", Number.class);
                    return n == null ? 0L : n.longValue();
                })
                .one()
                .block();

        org.assertj.core.api.Assertions.assertThat(cantidad).isEqualTo(1L);
        org.assertj.core.api.Assertions.assertThat(idPrimero).isNotNull();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private MultiValueMap<String, org.springframework.http.HttpEntity<?>> bodyReporte(
            Long idPlanDestino, String monto, String fechaTransferencia, String referencia) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("id_plan_destino", String.valueOf(idPlanDestino));
        builder.part("monto", monto);
        builder.part("fecha_transferencia", fechaTransferencia);
        builder.part("banco_origen", "Banco Test");
        builder.part("referencia", referencia);
        return builder.build();
    }

    private Long crearPlanPremium(String nombre) {
        @SuppressWarnings("rawtypes")
        Map body = webTestClient.post()
                .uri("/api/v1/planes")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "nombre", nombre,
                        "descripcion", "Plan destino idempotencia",
                        "precioMensual", 59.99,
                        "codigo", "PREM_IDEM_" + System.nanoTime()
                ))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        return Long.valueOf(body.get("id").toString());
    }

    private Long crearGymConPlan(String nombreGym, String ruc) {
        @SuppressWarnings("rawtypes")
        Map planBody = webTestClient.post()
                .uri("/api/v1/planes")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("nombre", "Plan base " + nombreGym, "descripcion", "desc", "precioMensual", 29.99))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        Integer planId = (Integer) planBody.get("id");

        @SuppressWarnings("rawtypes")
        Map registro = webTestClient.post()
                .uri("/api/v1/companias")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "nombre", nombreGym,
                        "ruc", ruc,
                        "correo", "idem@test.com",
                        "idPlan", planId,
                        "nombreSucursal", "Sede Idem",
                        "direccionSucursal", "Calle Idem 1"
                ))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        return Long.valueOf(registro.get("idCompania").toString());
    }
}
