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
import java.util.List;
import java.util.Map;

/**
 * REQ-SAAS-001 (Sub-fase 1.6, item #5): invalidación del cache de módulos al
 * aprobar un pago que activa un plan superior.
 *
 * <p><b>Comportamiento verificado:</b>
 * <ol>
 *   <li>La compañía nace con un plan que <b>no</b> incluye el módulo
 *       {@code finanzas} → {@code GET /modulos/check?codigo=finanzas} devuelve
 *       403 {@code modulo_no_incluido}. Esa respuesta populariza el cache.</li>
 *   <li>El owner reporta un pago para el plan Premium (que sí incluye finanzas).</li>
 *   <li>Un operador root aprueba el pago →
 *       {@link com.gymadmin.platform.application.service.AprobarPagoService}
 *       invoca {@code moduloCheckUseCase.invalidateCacheByCompania(idCompania)}
 *       tras crear la suscripción Premium.</li>
 *   <li>La siguiente llamada a {@code GET /modulos/check?codigo=finanzas} debe
 *       devolver 200 {@code permitido=true} — sin depender de valores rancios en cache.</li>
 * </ol>
 *
 * <p><b>Nota sobre Redis:</b> el bean {@code RedisModuloCheckCache} vigente en
 * {@code platform-service} es un stub no-op (todas las operaciones retornan
 * empty/0 — ver {@code docs/REDIS_REMOVAL.md}). Aún así, este test verifica el
 * contrato observable end-to-end: la lógica de invalidación se ejecuta (retorna 0
 * sin efecto lateral) y el CHECK re-computa el estado desde la BD, por lo que el
 * cambio de resultado es real. Con Redis reintegrado, el mismo test cubre además
 * la evicción de keys {@code modulo_check:{idCompania}:*}.
 */
@DisplayName("Cache invalidation al aprobar pago (Sub-fase 1.6 item #5)")
class CacheInvalidationAprobarPagoIntegrationTest extends BaseIntegrationTest {

    @Test
    @DisplayName("Free sin finanzas → 403; reporte+aprobación de Premium → siguiente check devuelve 200 permitido=true")
    void aprobarPagoInvalidaCache() {
        Integer caracFinanzas = crearCaracteristica("finanzas", "Finanzas", "finanzas");

        // Plan Free (sin finanzas)
        Long planFreeId = crearPlanBasico("Free Cache", "FREE_CACHE_" + System.nanoTime());

        // Plan Premium con finanzas
        Long planPremiumId = crearPlanBasico("Premium Cache", "PREM_CACHE_" + System.nanoTime());
        asociarCaracteristica(planPremiumId, caracFinanzas);

        Long idCompania = crearGymConPlan("Gym Cache", "1714000000001", planFreeId);

        // ── 1) Primer check: Free no tiene finanzas → 403 modulo_no_incluido ──
        webTestClient.get()
                .uri("/api/v1/modulos/check?id_compania={idC}&codigo={cod}", idCompania, "finanzas")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.permitido").isEqualTo(false)
                .jsonPath("$.razon").isEqualTo("modulo_no_incluido");

        // ── 2) Owner reporta pago hacia Premium ──────────────────────────────
        MultipartBodyBuilder builder = new MultipartBodyBuilder();
        builder.part("id_plan_destino", String.valueOf(planPremiumId));
        builder.part("monto", "59.99");
        builder.part("fecha_transferencia", LocalDate.now().toString());
        builder.part("banco_origen", "Banco Cache");
        builder.part("referencia", "TXN-CACHE-001");
        MultiValueMap<String, org.springframework.http.HttpEntity<?>> body = builder.build();

        @SuppressWarnings("rawtypes")
        Map pagoReportado = webTestClient.post()
                .uri("/api/v1/companias/{id}/pagos/reportar", idCompania)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtOwnerCompania(idCompania)))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(body))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        Long idPagoPendiente = Long.valueOf(pagoReportado.get("id").toString());

        // ── 3) Root aprueba el pago → AprobarPagoService invalida cache ──────
        webTestClient.post()
                .uri("/api/v1/plataforma/pagos-pendientes/{id}/aprobar", idPagoPendiente)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .exchange()
                .expectStatus().isOk();

        // ── 4) Nuevo check: ahora finanzas está permitido (200 permitido=true) ─
        webTestClient.get()
                .uri("/api/v1/modulos/check?id_compania={idC}&codigo={cod}", idCompania, "finanzas")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.permitido").isEqualTo(true)
                .jsonPath("$.plan").isEqualTo("Premium Cache");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Integer crearCaracteristica(String codigo, String nombre, String modulo) {
        @SuppressWarnings("rawtypes")
        Map body = webTestClient.post()
                .uri("/api/v1/caracteristicas")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("codigo", codigo, "nombre", nombre, "modulo", modulo))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        return (Integer) body.get("id");
    }

    private Long crearPlanBasico(String nombre, String codigo) {
        @SuppressWarnings("rawtypes")
        Map body = webTestClient.post()
                .uri("/api/v1/planes")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "nombre", nombre,
                        "descripcion", "Plan cache",
                        "precioMensual", 29.99,
                        "codigo", codigo
                ))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        return Long.valueOf(body.get("id").toString());
    }

    private void asociarCaracteristica(Long planId, Integer caracId) {
        webTestClient.put()
                .uri("/api/v1/planes/{id}/caracteristicas", planId)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("caracteristicaIds", List.of(caracId)))
                .exchange()
                .expectStatus().isOk();
    }

    private Long crearGymConPlan(String nombreGym, String ruc, Long planId) {
        @SuppressWarnings("rawtypes")
        Map registro = webTestClient.post()
                .uri("/api/v1/companias")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtSuperAdmin()))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "nombre", nombreGym,
                        "ruc", ruc,
                        "correo", "cache@test.com",
                        "idPlan", planId,
                        "nombreSucursal", "Sede Cache",
                        "direccionSucursal", "Calle Cache 1"
                ))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();
        return Long.valueOf(registro.get("idCompania").toString());
    }
}
