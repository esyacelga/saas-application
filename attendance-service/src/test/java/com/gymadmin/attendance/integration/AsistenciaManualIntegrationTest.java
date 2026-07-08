package com.gymadmin.attendance.integration;

import com.gymadmin.attendance.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.Map;

/**
 * Tests de integración para registro manual de asistencia.
 * El flujo de QR y manual requieren Core Service activo; estos tests cubren
 * la lógica de BD y endpoints que no dependen de servicios externos.
 */
@DisplayName("Asistencia Manual — integración BD + endpoint")
class AsistenciaManualIntegrationTest extends BaseIntegrationTest {

    private static final Integer COMPANIA = 1;
    private static final Integer SUCURSAL = 1;

    // ── TC-ASI-DB-001 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("UNIQUE(id_membresia, fecha) impide segunda asistencia el mismo día")
    void unicidadMembresiaFecha() {
        Integer idCliente = insertarClienteCore(COMPANIA, SUCURSAL);
        Integer idMembresia = insertarMembresia(idCliente, COMPANIA);
        String hoy = LocalDate.now().toString();

        insertarAsistencia(COMPANIA, SUCURSAL, idCliente, idMembresia, hoy, "08:00:00", "manual");

        org.junit.jupiter.api.Assertions.assertThrows(Exception.class, () ->
                insertarAsistencia(COMPANIA, SUCURSAL, idCliente, idMembresia, hoy, "09:00:00", "manual")
        );
    }

    // ── TC-ASI-DB-002 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Inserción directa persiste correctamente todos los campos")
    void insertarAsistenciaDirecta() {
        Integer idCliente = insertarClienteCore(COMPANIA, SUCURSAL);
        Integer idMembresia = insertarMembresia(idCliente, COMPANIA);
        String hoy = LocalDate.now().toString();

        Long id = insertarAsistencia(COMPANIA, SUCURSAL, idCliente, idMembresia, hoy, "07:30:00", "qr_cliente");

        org.junit.jupiter.api.Assertions.assertNotNull(id);
        org.junit.jupiter.api.Assertions.assertTrue(id > 0);

        Long count = databaseClient.sql(
                        "SELECT COUNT(*) FROM asistencia.asistencias WHERE id = :id")
                .bind("id", id)
                .map(row -> row.get(0, Long.class))
                .one()
                .block();

        org.junit.jupiter.api.Assertions.assertEquals(1L, count);
    }

    // ── TC-ASI-SEC-001 — Entrenador no puede registrar manual ─────────────────

    @Test
    @DisplayName("POST /asistencias/manual con JWT entrenador retorna 403")
    void entrenadorNoPuedeRegistrarManual() {
        Map<String, Object> body = Map.of(
                "idCliente", 5,
                "fecha", LocalDate.now().toString(),
                "horaEntrada", "09:00:00"
        );

        webTestClient.post()
                .uri("/api/v1/asistencias/manual")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtEntrenador(COMPANIA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── TC-ASI-SEC-002 — Cliente no puede registrar manual ────────────────────

    @Test
    @DisplayName("POST /asistencias/manual con JWT cliente retorna 403")
    void clienteNoPuedeRegistrarManual() {
        Map<String, Object> body = Map.of("idCliente", 5);

        webTestClient.post()
                .uri("/api/v1/asistencias/manual")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtCliente(5, COMPANIA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── TC-ASI-SEC-003 — Override sin Dueño retorna 403 ──────────────────────

    @Test
    @DisplayName("POST /asistencias/manual/override con JWT recepción retorna 403")
    void recepcionNoPuedeOverride() {
        Map<String, Object> body = Map.of(
                "idCliente", 5,
                "fecha", LocalDate.now().toString(),
                "horaEntrada", "09:00:00",
                "motivoOverride", "Prueba"
        );

        webTestClient.post()
                .uri("/api/v1/asistencias/manual/override")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtRecepcion(COMPANIA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── TC-ASI-SEC-004 — Sin JWT retorna 401 ──────────────────────────────────

    @Test
    @DisplayName("POST /asistencias/manual sin JWT retorna 401")
    void sinJwtRetorna401() {
        webTestClient.post()
                .uri("/api/v1/asistencias/manual")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("idCliente", 5))
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // ── TC-ASI-SEC-005 — QR solo para clientes ────────────────────────────────

    @Test
    @DisplayName("POST /asistencias/qr con JWT staff retorna 403")
    void staffNoPuedeUsarEndpointQr() {
        webTestClient.post()
                .uri("/api/v1/asistencias/qr")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtRecepcion(COMPANIA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("qrToken", "gym-1-abc123"))
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── TC-ASI-HIST-001 — Historial desde BD ──────────────────────────────────

    @Test
    @DisplayName("GET /clientes/{id}/asistencias retorna historial con filtro de fechas")
    void historialClienteConFiltro() {
        Integer idCliente = insertarClienteCore(COMPANIA, SUCURSAL);
        Integer idMembresia1 = insertarMembresia(idCliente, COMPANIA);
        Integer idMembresia2 = insertarMembresia(idCliente, COMPANIA);

        insertarAsistencia(COMPANIA, SUCURSAL, idCliente, idMembresia1, "2026-05-10", "08:00:00", "manual");
        insertarAsistencia(COMPANIA, SUCURSAL, idCliente, idMembresia1, "2026-05-15", "08:00:00", "manual");
        insertarAsistencia(COMPANIA, SUCURSAL, idCliente, idMembresia2, "2026-06-01", "08:00:00", "manual");

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/clientes/" + idCliente + "/asistencias")
                        .queryParam("desde", "2026-05-01")
                        .queryParam("hasta", "2026-05-31")
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(COMPANIA)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.total_en_periodo").isEqualTo(2)
                .jsonPath("$.asistencias.length()").isEqualTo(2);
    }

    // ── TC-ASI-HIST-002 — Cliente solo ve las suyas ───────────────────────────

    @Test
    @DisplayName("GET /clientes/{id}/asistencias con JWT cliente ajeno retorna 403")
    void clienteSoloVeLasSuyas() {
        Integer idClienteReal = insertarClienteCore(COMPANIA, SUCURSAL);

        webTestClient.get()
                .uri("/api/v1/clientes/" + (idClienteReal + 99) + "/asistencias")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtCliente(idClienteReal, COMPANIA)))
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── TC-ASI-HIST-003 — Cliente ve las suyas ───────────────────────────────

    @Test
    @DisplayName("GET /clientes/{id}/asistencias con JWT del propio cliente retorna 200")
    void clienteVeLasSuyas() {
        Integer idCliente = insertarClienteCore(COMPANIA, SUCURSAL);
        Integer idMembresia = insertarMembresia(idCliente, COMPANIA);
        insertarAsistencia(COMPANIA, SUCURSAL, idCliente, idMembresia, "2026-05-20", "08:00:00", "qr_cliente");

        webTestClient.get()
                .uri("/api/v1/clientes/" + idCliente + "/asistencias")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtCliente(idCliente, COMPANIA)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.asistencias").isArray();
    }

    // ── TC-ASI-30D-001 — Últimos 30 días ──────────────────────────────────────

    @Test
    @DisplayName("GET /clientes/{id}/asistencias/ultimos-30 retorna estructura con 30 entradas")
    void ultimos30DiasEstructura() {
        Integer idCliente = insertarClienteCore(COMPANIA, SUCURSAL);
        Integer idMembresia = insertarMembresia(idCliente, COMPANIA);

        insertarAsistencia(COMPANIA, SUCURSAL, idCliente, idMembresia,
                LocalDate.now().toString(), "08:00:00", "qr_cliente");
        insertarAsistencia(COMPANIA, SUCURSAL, idCliente, idMembresia,
                LocalDate.now().minusDays(1).toString(), "08:00:00", "qr_cliente");

        webTestClient.get()
                .uri("/api/v1/clientes/" + idCliente + "/asistencias/ultimos-30")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(COMPANIA)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.clienteId").isEqualTo(idCliente)
                .jsonPath("$.diasAsistidos").isEqualTo(2)
                .jsonPath("$.detalle.length()").isEqualTo(30);
    }

    // ── TC-ASI-HOY-001 — Dashboard hoy ────────────────────────────────────────

    @Test
    @DisplayName("GET /asistencias/hoy retorna resumen del día con conteos por método")
    void asistenciasHoyResumen() {
        String hoy = LocalDate.now().toString();

        Integer id1 = insertarClienteCore(COMPANIA, SUCURSAL);
        Integer mem1 = insertarMembresia(id1, COMPANIA);
        insertarAsistencia(COMPANIA, SUCURSAL, id1, mem1, hoy, "07:00:00", "qr_cliente");

        Integer id2 = insertarClienteCore(COMPANIA, SUCURSAL);
        Integer mem2 = insertarMembresia(id2, COMPANIA);
        insertarAsistencia(COMPANIA, SUCURSAL, id2, mem2, hoy, "07:15:00", "manual");

        Integer id3 = insertarClienteCore(COMPANIA, SUCURSAL);
        Integer mem3 = insertarMembresia(id3, COMPANIA);
        insertarAsistencia(COMPANIA, SUCURSAL, id3, mem3, hoy, "07:30:00", "qr_cliente");

        webTestClient.get()
                .uri("/api/v1/asistencias/hoy")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(COMPANIA)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalEntradas").isEqualTo(3)
                .jsonPath("$.porMetodo.qr_cliente").isEqualTo(2)
                .jsonPath("$.porMetodo.manual").isEqualTo(1);
    }

    // ── TC-ASI-HOY-002 — Dashboard hoy filtrado por sucursal ──────────────────

    @Test
    @DisplayName("GET /asistencias/hoy?id_sucursal=2 no devuelve entradas de otra sucursal")
    void asistenciasHoyFiltradoPorSucursal() {
        String hoy = LocalDate.now().toString();

        Integer id1 = insertarClienteCore(COMPANIA, 1);
        Integer mem1 = insertarMembresia(id1, COMPANIA);
        insertarAsistencia(COMPANIA, 1, id1, mem1, hoy, "08:00:00", "manual");

        Integer id2 = insertarClienteCore(COMPANIA, 2);
        Integer mem2 = insertarMembresia(id2, COMPANIA);
        insertarAsistencia(COMPANIA, 2, id2, mem2, hoy, "08:00:00", "manual");

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/asistencias/hoy")
                        .queryParam("idSucursal", 2)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(COMPANIA)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totalEntradas").isEqualTo(1);
    }

    // ── TC-ASI-RACHA-001 — Racha perfecta ─────────────────────────────────────

    @Test
    @DisplayName("GET /clientes/{id}/asistencias/racha-perfecta retorna estructura correcta")
    void rachaPerfectaEstructura() {
        Integer idCliente = insertarClienteCore(COMPANIA, SUCURSAL);

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/clientes/" + idCliente + "/asistencias/racha-perfecta")
                        .queryParam("meses", 1)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(COMPANIA)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.rachaPerfecta").exists()
                .jsonPath("$.diasAsistidos").isEqualTo(0)
                .jsonPath("$.diasConMembresia").isNumber();
    }

    // ── TC-ASI-VALID-001 — Validación request QR ──────────────────────────────

    @Test
    @DisplayName("POST /asistencias/qr sin qr_token retorna 400")
    void qrSinTokenRetorna400() {
        webTestClient.post()
                .uri("/api/v1/asistencias/qr")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtCliente(5, COMPANIA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of())
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ── TC-ASI-VALID-002 — Validación request manual ──────────────────────────

    @Test
    @DisplayName("POST /asistencias/manual sin idCliente retorna 400")
    void manualSinIdClienteRetorna400() {
        webTestClient.post()
                .uri("/api/v1/asistencias/manual")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtRecepcion(COMPANIA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of())
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ── TC-ASI-OVERRIDE-001 — Override happy path ─────────────────────────────

    @Test
    @DisplayName("POST /asistencias/manual/override con JWT dueño retorna 201 y persiste asistencia sin membresía")
    void duenoRegistraOverride() {
        Integer idCliente = insertarClienteCore(COMPANIA, SUCURSAL);

        Map<String, Object> body = Map.of(
                "idCliente", idCliente,
                "idSucursal", SUCURSAL,
                "fecha", LocalDate.now().toString(),
                "horaEntrada", "10:30:00",
                "motivoOverride", "Pago en efectivo pendiente de registro"
        );

        webTestClient.post()
                .uri("/api/v1/asistencias/manual/override")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(COMPANIA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").exists()
                .jsonPath("$.idCliente").isEqualTo(idCliente)
                .jsonPath("$.idCompania").isEqualTo(COMPANIA)
                .jsonPath("$.metodoRegistro").isEqualTo("manual");
    }

    // ── TC-ASI-STATS-001 — Estadísticas happy path ────────────────────────────

    @Test
    @DisplayName("GET /asistencias/estadisticas retorna KPIs del mes con estructura completa")
    void estadisticasMesActual() {
        String hoy = LocalDate.now().toString();

        Integer id1 = insertarClienteCore(COMPANIA, SUCURSAL);
        Integer mem1 = insertarMembresia(id1, COMPANIA);
        insertarAsistencia(COMPANIA, SUCURSAL, id1, mem1, hoy, "08:00:00", "manual");

        Integer id2 = insertarClienteCore(COMPANIA, SUCURSAL);
        Integer mem2 = insertarMembresia(id2, COMPANIA);
        insertarAsistencia(COMPANIA, SUCURSAL, id2, mem2, hoy, "09:00:00", "qr_cliente");

        webTestClient.get()
                .uri("/api/v1/asistencias/estadisticas")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(COMPANIA)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.periodo").isNotEmpty()
                .jsonPath("$.totalEntradas").isEqualTo(2)
                .jsonPath("$.promedioDiario").isNumber();
    }
}
