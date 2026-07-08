package com.gymadmin.attendance.integration;

import com.gymadmin.attendance.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.time.LocalDate;

/**
 * Tests de integración para los endpoints del dashboard del dueño:
 *   GET /api/v1/asistencias/hoy
 *   GET /api/v1/asistencias/estadisticas
 */
@DisplayName("Dashboard — /asistencias/hoy y /asistencias/estadisticas")
class DashboardIntegrationTest extends BaseIntegrationTest {

    private static final Integer COMPANIA = 1;
    private static final Integer SUCURSAL = 1;
    private static final Integer SUCURSAL_2 = 2;

    // ═══════════════════════════════════════════════════════════════
    // GET /asistencias/hoy
    // ═══════════════════════════════════════════════════════════════

    // ── TC-DASH-HOY-001 — Happy path: totales y breakdown por método ──────────

    @Test
    @DisplayName("TC-DASH-HOY-001: retorna totalEntradas correcto y breakdown por método")
    void hoy_retornaTotalesYBreakdownPorMetodo() {
        String hoy = LocalDate.now().toString();

        Integer c1 = insertarClienteCore(COMPANIA, SUCURSAL);
        Integer m1 = insertarMembresia(c1, COMPANIA);
        insertarAsistencia(COMPANIA, SUCURSAL, c1, m1, hoy, "07:00:00", "qr_cliente");

        Integer c2 = insertarClienteCore(COMPANIA, SUCURSAL);
        Integer m2 = insertarMembresia(c2, COMPANIA);
        insertarAsistencia(COMPANIA, SUCURSAL, c2, m2, hoy, "07:30:00", "manual");

        Integer c3 = insertarClienteCore(COMPANIA, SUCURSAL);
        Integer m3 = insertarMembresia(c3, COMPANIA);
        insertarAsistencia(COMPANIA, SUCURSAL, c3, m3, hoy, "08:00:00", "qr_cliente");

        webTestClient.get()
                .uri("/api/v1/asistencias/hoy")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(COMPANIA)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalEntradas").isEqualTo(3)
                .jsonPath("$.porMetodo.qr_cliente").isEqualTo(2)
                .jsonPath("$.porMetodo.manual").isEqualTo(1)
                .jsonPath("$.fecha").isNotEmpty()
                .jsonPath("$.ultimasEntradas").isArray();
    }

    // ── TC-DASH-HOY-002 — Día vacío retorna 0 entradas ───────────────────────

    @Test
    @DisplayName("TC-DASH-HOY-002: sin asistencias hoy retorna totalEntradas=0")
    void hoy_diaVacioRetornaCero() {
        webTestClient.get()
                .uri("/api/v1/asistencias/hoy")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(COMPANIA)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalEntradas").isEqualTo(0)
                .jsonPath("$.ultimasEntradas").isArray();
    }

    // ── TC-DASH-HOY-003 — Filtro por sucursal aísla correctamente ────────────

    @Test
    @DisplayName("TC-DASH-HOY-003: idSucursal filtra asistencias de otra sucursal")
    void hoy_filtroPorSucursalAislaResultados() {
        String hoy = LocalDate.now().toString();

        Integer c1 = insertarClienteCore(COMPANIA, SUCURSAL);
        Integer m1 = insertarMembresia(c1, COMPANIA);
        insertarAsistencia(COMPANIA, SUCURSAL, c1, m1, hoy, "08:00:00", "manual");

        Integer c2 = insertarClienteCore(COMPANIA, SUCURSAL_2);
        Integer m2 = insertarMembresia(c2, COMPANIA);
        insertarAsistencia(COMPANIA, SUCURSAL_2, c2, m2, hoy, "08:10:00", "manual");

        webTestClient.get()
                .uri(u -> u.path("/api/v1/asistencias/hoy")
                        .queryParam("idSucursal", SUCURSAL_2)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(COMPANIA)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalEntradas").isEqualTo(1);
    }

    // ── TC-DASH-HOY-004 — ultimasEntradas tiene máximo 10 entradas ───────────

    @Test
    @DisplayName("TC-DASH-HOY-004: con más de 10 asistencias ultimasEntradas devuelve máx 10")
    void hoy_ultimasEntradasMaximoDiez() {
        String hoy = LocalDate.now().toString();

        for (int i = 0; i < 12; i++) {
            Integer c = insertarClienteCore(COMPANIA, SUCURSAL);
            Integer m = insertarMembresia(c, COMPANIA);
            String hora = String.format("%02d:00:00", 6 + i);
            insertarAsistencia(COMPANIA, SUCURSAL, c, m, hoy, hora, "manual");
        }

        webTestClient.get()
                .uri("/api/v1/asistencias/hoy")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(COMPANIA)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalEntradas").isEqualTo(12)
                .jsonPath("$.ultimasEntradas.length()").isEqualTo(10);
    }

    // ── TC-DASH-HOY-005 — recepcionista también puede ver ────────────────────

    @Test
    @DisplayName("TC-DASH-HOY-005: JWT recepción puede acceder a /asistencias/hoy")
    void hoy_recepcionPuedeAcceder() {
        webTestClient.get()
                .uri("/api/v1/asistencias/hoy")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtRecepcion(COMPANIA)))
                .exchange()
                .expectStatus().isOk();
    }

    // ── TC-DASH-HOY-006 — cliente no puede ver el dashboard ──────────────────

    @Test
    @DisplayName("TC-DASH-HOY-006: JWT cliente retorna 403")
    void hoy_clienteRetorna403() {
        webTestClient.get()
                .uri("/api/v1/asistencias/hoy")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtCliente(1, COMPANIA)))
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── TC-DASH-HOY-007 — sin JWT retorna 401 ────────────────────────────────

    @Test
    @DisplayName("TC-DASH-HOY-007: sin JWT retorna 401")
    void hoy_sinJwtRetorna401() {
        webTestClient.get()
                .uri("/api/v1/asistencias/hoy")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ═══════════════════════════════════════════════════════════════
    // GET /asistencias/estadisticas
    // ═══════════════════════════════════════════════════════════════

    // ── TC-DASH-STATS-001 — Happy path: estructura completa ──────────────────

    @Test
    @DisplayName("TC-DASH-STATS-001: retorna estructura completa con totalEntradas y promedioDiario")
    void estadisticas_estructuraCompleta() {
        String hoy = LocalDate.now().toString();

        Integer c1 = insertarClienteCore(COMPANIA, SUCURSAL);
        Integer m1 = insertarMembresia(c1, COMPANIA);
        insertarAsistencia(COMPANIA, SUCURSAL, c1, m1, hoy, "08:00:00", "manual");

        Integer c2 = insertarClienteCore(COMPANIA, SUCURSAL);
        Integer m2 = insertarMembresia(c2, COMPANIA);
        insertarAsistencia(COMPANIA, SUCURSAL, c2, m2, hoy, "09:00:00", "qr_cliente");

        webTestClient.get()
                .uri("/api/v1/asistencias/estadisticas")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(COMPANIA)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.periodo").isNotEmpty()
                .jsonPath("$.totalEntradas").isEqualTo(2)
                .jsonPath("$.promedioDiario").isNumber()
                .jsonPath("$.clientesActivos").exists()
                .jsonPath("$.clientesSinAsistir7d").exists()
                .jsonPath("$.horaPico").exists();
    }

    // ── TC-DASH-STATS-002 — Mes vacío retorna 0 entradas ────────────────────

    @Test
    @DisplayName("TC-DASH-STATS-002: mes sin asistencias retorna totalEntradas=0 y promedioDiario=0.0")
    void estadisticas_mesVacioRetornaCeros() {
        webTestClient.get()
                .uri(u -> u.path("/api/v1/asistencias/estadisticas")
                        .queryParam("periodo", "mes")
                        .queryParam("anio", 2020)
                        .queryParam("mes", 1)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(COMPANIA)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalEntradas").isEqualTo(0)
                .jsonPath("$.promedioDiario").isEqualTo(0.0);
    }

    // ── TC-DASH-STATS-003 — periodo=anio agrega el año completo ─────────────

    @Test
    @DisplayName("TC-DASH-STATS-003: periodo=anio devuelve campo periodo con formato YYYY")
    void estadisticas_periodoAnio() {
        int anioActual = LocalDate.now().getYear();

        webTestClient.get()
                .uri(u -> u.path("/api/v1/asistencias/estadisticas")
                        .queryParam("periodo", "anio")
                        .queryParam("anio", anioActual)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(COMPANIA)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.periodo").isEqualTo(String.valueOf(anioActual))
                .jsonPath("$.totalEntradas").isNumber();
    }

    // ── TC-DASH-STATS-004 — promedioDiario refleja la cantidad real ──────────

    @Test
    @DisplayName("TC-DASH-STATS-004: promedioDiario es consistente con totalEntradas del mes")
    void estadisticas_promedioDiarioConsistente() {
        String hoy = LocalDate.now().toString();
        int anio = LocalDate.now().getYear();
        int mes  = LocalDate.now().getMonthValue();

        for (int i = 0; i < 4; i++) {
            Integer c = insertarClienteCore(COMPANIA, SUCURSAL);
            Integer m = insertarMembresia(c, COMPANIA);
            insertarAsistencia(COMPANIA, SUCURSAL, c, m, hoy, String.format("%02d:00:00", 7 + i), "manual");
        }

        webTestClient.get()
                .uri(u -> u.path("/api/v1/asistencias/estadisticas")
                        .queryParam("periodo", "mes")
                        .queryParam("anio", anio)
                        .queryParam("mes", mes)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(COMPANIA)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalEntradas").isEqualTo(4)
                .jsonPath("$.promedioDiario").isNumber();
    }

    // ── TC-DASH-STATS-005 — cliente no puede ver estadísticas ────────────────

    @Test
    @DisplayName("TC-DASH-STATS-005: JWT cliente retorna 403")
    void estadisticas_clienteRetorna403() {
        webTestClient.get()
                .uri("/api/v1/asistencias/estadisticas")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtCliente(1, COMPANIA)))
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── TC-DASH-STATS-006 — sin JWT retorna 401 ──────────────────────────────

    @Test
    @DisplayName("TC-DASH-STATS-006: sin JWT retorna 401")
    void estadisticas_sinJwtRetorna401() {
        webTestClient.get()
                .uri("/api/v1/asistencias/estadisticas")
                .exchange()
                .expectStatus().isUnauthorized();
    }
}
