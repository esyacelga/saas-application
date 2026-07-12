package com.gymadmin.platform.integration;

import com.gymadmin.platform.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * REQ-SAAS-001 (Sub-fase 1.6, item #5): concurrencia en aprobación de pago.
 *
 * <p>{@code AprobarPagoService} usa un <b>UPDATE atómico</b> a nivel de row
 * ({@code UPDATE tenant.pagos_pendientes_validacion SET estado='aprobado'
 * WHERE id = :id AND estado = 'pendiente'}) y consulta el número de filas
 * afectadas. Si {@code rowsUpdated == 0}, otro operador ya ganó y se
 * emite {@link com.gymadmin.platform.domain.exception.PagoYaProcesadoException}
 * (mapea a HTTP 409 con código {@code pago_ya_procesado}).
 *
 * <p><b>Nota:</b> el servicio NO usa {@code pg_advisory_lock} — la garantía la
 * proporciona el UPDATE atómico con predicado {@code estado='pendiente'} (la
 * cláusula {@code WHERE} funciona como lock optimista implícito).
 *
 * <p>Simulación: dos operadores root disparan la aprobación en paralelo sobre
 * el mismo pago. Se esperan exactamente:
 * <ul>
 *   <li>1 request con 200 OK (ganador).</li>
 *   <li>1 request con 409 CONFLICT / {@code pago_ya_procesado} (perdedor).</li>
 *   <li>Exactamente 1 fila {@code tenant.compania_planes} nueva del plan destino.</li>
 *   <li>Exactamente 1 evento {@code PAGO_APROBADO} en {@code saas.actividad_plataforma}.</li>
 * </ul>
 */
@DisplayName("Aprobar pago — concurrencia (Sub-fase 1.6 item #5)")
class AprobarPagoConcurrenciaIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("2 aprobaciones concurrentes → una gana (200), otra falla (409 pago_ya_procesado)")
    void aprobacionConcurrenteExclusiva() {
        Long idCompania = crearGymConPlanBasico("Gym Concurrencia", "1712000000001");
        Long idPlanDestino = crearPlanPremium("Premium Concurrencia");

        Long idPagoPendiente = reportarPago(idCompania, idPlanDestino, "TXN-CONC-001", "59.99");

        // ── Dos aprobaciones disparadas en paralelo ──────────────────────────
        // Cada intento se subscribe en un thread distinto de boundedElastic para
        // que ambos HTTP calls se ejecuten realmente concurrentes contra el mismo pago.
        Mono<Integer> intento1 = Mono.fromCallable(() -> intentoAprobacion(idPagoPendiente))
                .subscribeOn(Schedulers.boundedElastic());
        Mono<Integer> intento2 = Mono.fromCallable(() -> intentoAprobacion(idPagoPendiente))
                .subscribeOn(Schedulers.boundedElastic());

        List<Integer> statuses = Mono.zip(intento1, intento2)
                .map(tuple -> List.of(tuple.getT1(), tuple.getT2()))
                .block(Duration.ofSeconds(30));

        org.assertj.core.api.Assertions.assertThat(statuses).isNotNull().hasSize(2);

        AtomicInteger exitos = new AtomicInteger();
        AtomicInteger conflictos = new AtomicInteger();
        for (Integer status : statuses) {
            if (status == 200) exitos.incrementAndGet();
            else if (status == 409) conflictos.incrementAndGet();
        }

        org.assertj.core.api.Assertions.assertThat(exitos.get())
                .as("exactamente 1 aprobación debe devolver 200. Statuses recibidos: " + statuses)
                .isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(conflictos.get())
                .as("exactamente 1 aprobación debe devolver 409 pago_ya_procesado. Statuses recibidos: " + statuses)
                .isEqualTo(1);

        // ── Verificación: solo 1 CompaniaPlan nuevo del plan destino ─────────
        Long companiaPlanesDelPremium = databaseClient.sql(
                "SELECT COUNT(*) AS c FROM tenant.compania_planes " +
                "WHERE id_compania = :idC AND id_plan = :idP")
                .bind("idC", idCompania)
                .bind("idP", idPlanDestino)
                .map((row, meta) -> {
                    Number n = row.get("c", Number.class);
                    return n == null ? 0L : n.longValue();
                })
                .one()
                .block();

        org.assertj.core.api.Assertions.assertThat(companiaPlanesDelPremium)
                .as("solo debe existir 1 suscripción nueva del plan destino")
                .isEqualTo(1L);

        // ── Verificación: exactamente 1 evento PAGO_APROBADO ─────────────────
        Long eventos = databaseClient.sql(
                "SELECT COUNT(*) AS c FROM saas.actividad_plataforma " +
                "WHERE id_compania = :id AND tipo_evento = 'PAGO_APROBADO'")
                .bind("id", idCompania)
                .map((row, meta) -> {
                    Number n = row.get("c", Number.class);
                    return n == null ? 0L : n.longValue();
                })
                .one()
                .block();

        org.assertj.core.api.Assertions.assertThat(eventos)
                .as("solo debe registrarse 1 evento PAGO_APROBADO")
                .isEqualTo(1L);

        // ── Verificación: el pago quedó APROBADO ─────────────────────────────
        String estadoFinal = databaseClient.sql(
                "SELECT estado FROM tenant.pagos_pendientes_validacion WHERE id = :id")
                .bind("id", idPagoPendiente)
                .map((row, meta) -> row.get("estado", String.class))
                .one()
                .block();

        org.assertj.core.api.Assertions.assertThat(estadoFinal).isEqualTo("aprobado");
    }

    /**
     * Ejecuta un POST bloqueante hacia {@code /aprobar} y devuelve el status HTTP.
     * Se llama desde dentro de {@code Mono.fromCallable(...).subscribeOn(boundedElastic)}
     * para lograr concurrencia real entre los dos requests.
     */
    private int intentoAprobacion(Long idPagoPendiente) {
        EntityExchangeResult<byte[]> result = webTestClient.post()
                .uri("/api/v1/plataforma/pagos-pendientes/{id}/aprobar", idPagoPendiente)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .exchange()
                .expectBody()
                .returnResult();
        return result.getStatus().value();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Long reportarPago(Long idCompania, Long idPlanDestino, String referencia, String monto) {
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("id_plan_destino", String.valueOf(idPlanDestino));
        builder.part("monto", monto);
        builder.part("fecha_transferencia", LocalDate.now().toString());
        builder.part("banco_origen", "Banco Test");
        builder.part("referencia", referencia);
        MultiValueMap<String, org.springframework.http.HttpEntity<?>> body = builder.build();

        @SuppressWarnings("rawtypes")
        Map response = webTestClient.post()
                .uri("/api/v1/companias/{id}/pagos/reportar", idCompania)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtOwnerCompania(idCompania)))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        return Long.valueOf(response.get("id").toString());
    }

    private Long crearPlanPremium(String nombre) {
        @SuppressWarnings("rawtypes")
        Map body = webTestClient.post()
                .uri("/api/v1/planes")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "nombre", nombre,
                        "descripcion", "Plan destino concurrencia",
                        "precioMensual", 59.99,
                        "codigo", "PREM_CONC_" + System.nanoTime()
                ))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        return Long.valueOf(body.get("id").toString());
    }

    private Long crearGymConPlanBasico(String nombreGym, String ruc) {
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
                        "correo", "conc@test.com",
                        "idPlan", planId,
                        "nombreSucursal", "Sede Conc",
                        "direccionSucursal", "Calle Conc 1"
                ))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        return Long.valueOf(registro.get("idCompania").toString());
    }
}
