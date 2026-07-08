package com.gymadmin.attendance.integration;

import com.gymadmin.attendance.BaseIntegrationTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@DisplayName("Mensajes Log — integración BD + endpoint")
class MensajeLogIntegrationTest extends BaseIntegrationTest {

    private static final Integer COMPANIA = 1;
    private static final Integer SUCURSAL = 1;

    // ── TC-MSG-DB-001 — INSERT directo en mensajes_log ────────────────────────

    @Test
    @DisplayName("INSERT en mensajes_log persiste todos los campos correctamente")
    void insertarMensajeDirecto() {
        Integer idCliente = insertarClienteCore(COMPANIA, SUCURSAL);
        Integer idPlantilla = insertarPlantilla(COMPANIA, SUCURSAL,
                "ausencia_2d", "P1", "Hola {nombre}");

        Long id = databaseClient.sql("""
                INSERT INTO asistencia.mensajes_log
                  (id_compania, id_sucursal, id_cliente, id_plantilla,
                   tipo, canal, contenido, estado, fecha_programada, creacion_usuario)
                VALUES (:comp, :suc, :cli, :plt,
                        'ausencia_2d', 'whatsapp', 'Hola Test', 'enviado',
                        NOW(), 'test')
                RETURNING id
                """)
                .bind("comp", COMPANIA).bind("suc", SUCURSAL)
                .bind("cli", idCliente).bind("plt", idPlantilla)
                .map(row -> row.get("id", Long.class))
                .one()
                .block();

        Assertions.assertNotNull(id);

        Map<String, Object> row = databaseClient.sql(
                        "SELECT tipo, canal, estado FROM asistencia.mensajes_log WHERE id = :id")
                .bind("id", id)
                .fetch().one().block();

        Assertions.assertEquals("ausencia_2d", row.get("tipo"));
        Assertions.assertEquals("whatsapp", row.get("canal"));
        Assertions.assertEquals("enviado", row.get("estado"));
    }

    // ── TC-MSG-001 — Enviar mensaje manual por endpoint ───────────────────────

    @Test
    @DisplayName("POST /mensajes/enviar crea log con estado enviado o fallido")
    void enviarMensajeManual() {
        Integer idCliente = insertarClienteCore(COMPANIA, SUCURSAL);
        Integer idPlantilla = insertarPlantilla(COMPANIA, SUCURSAL,
                "motivacional", "Motivacional", "Hola {nombre} te esperamos.");

        Map<String, Object> body = Map.of(
                "idCliente", idCliente,
                "canal", "whatsapp",
                "idPlantilla", idPlantilla
        );

        webTestClient.post()
                .uri("/api/v1/mensajes/enviar")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtRecepcion(COMPANIA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isNumber()
                .jsonPath("$.tipo").isEqualTo("motivacional")
                .jsonPath("$.canal").isEqualTo("whatsapp")
                .jsonPath("$.contenido").isEqualTo("Hola {nombre} te esperamos.")
                .jsonPath("$.estado").value(estado ->
                        Assertions.assertTrue(
                                List.of("enviado", "fallido").contains(estado),
                                "estado debe ser enviado o fallido, fue: " + estado));
    }

    // ── TC-MSG-002 — Plantilla inexistente retorna 404 ────────────────────────

    @Test
    @DisplayName("POST /mensajes/enviar con plantilla inexistente retorna 404")
    void enviarConPlantillaInexistente() {
        webTestClient.post()
                .uri("/api/v1/mensajes/enviar")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtRecepcion(COMPANIA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("idCliente", 5, "canal", "whatsapp", "idPlantilla", 99999))
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── TC-MSG-003 — Listar mensajes con filtros ──────────────────────────────

    @Test
    @DisplayName("GET /mensajes filtra por id_compania del JWT")
    void listarMensajesFiltradosPorCompania() {
        Integer idClienteComp1 = insertarClienteCore(1, 1);
        Integer idClienteComp2 = insertarClienteCore(2, 1);
        Integer plt1 = insertarPlantilla(1, 1, "ausencia_2d", "Gym1-P1", "Texto 1");
        Integer plt2 = insertarPlantilla(2, 1, "ausencia_2d", "Gym2-P1", "Texto 2");

        insertarMensajeLog(1, 1, idClienteComp1, plt1, "ausencia_2d", "whatsapp", "Texto 1", "enviado");
        insertarMensajeLog(2, 1, idClienteComp2, plt2, "ausencia_2d", "whatsapp", "Texto 2", "enviado");

        webTestClient.get()
                .uri("/api/v1/mensajes")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(1)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.total").isEqualTo(1)
                .jsonPath("$.datos[0].tipo").isEqualTo("ausencia_2d");
    }

    // ── TC-MSG-004 — Listar filtrando por estado ──────────────────────────────

    @Test
    @DisplayName("GET /mensajes?estado=fallido retorna solo los fallidos")
    void listarFiltrandoPorEstado() {
        Integer idCliente1 = insertarClienteCore(COMPANIA, SUCURSAL);
        Integer idCliente2 = insertarClienteCore(COMPANIA, SUCURSAL);
        Integer idCliente3 = insertarClienteCore(COMPANIA, SUCURSAL);
        Integer plt = insertarPlantilla(COMPANIA, SUCURSAL, "recuperacion_5d", "P", "C");

        insertarMensajeLog(COMPANIA, SUCURSAL, idCliente1, plt, "recuperacion_5d", "whatsapp", "C", "enviado");
        insertarMensajeLog(COMPANIA, SUCURSAL, idCliente2, plt, "recuperacion_5d", "whatsapp", "C", "fallido");
        insertarMensajeLog(COMPANIA, SUCURSAL, idCliente3, plt, "recuperacion_5d", "whatsapp", "C", "fallido");

        webTestClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/v1/mensajes")
                        .queryParam("estado", "fallido")
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(COMPANIA)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.total").isEqualTo(2);
    }

    // ── TC-MSG-005 — Reenviar mensaje fallido ─────────────────────────────────

    @Test
    @DisplayName("POST /mensajes/reenviar/{id} reintenta mensaje fallido")
    void reenviarMensajeFallido() {
        Integer idCliente = insertarClienteCore(COMPANIA, SUCURSAL);
        Integer plt = insertarPlantilla(COMPANIA, SUCURSAL, "vencimiento_3d", "P", "Texto {nombre}");
        Long idMsg = insertarMensajeLog(COMPANIA, SUCURSAL, idCliente, plt,
                "vencimiento_3d", "whatsapp", "Texto Test", "fallido");

        webTestClient.post()
                .uri("/api/v1/mensajes/reenviar/{id}", idMsg)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(COMPANIA)))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(idMsg.intValue())
                .jsonPath("$.estado").value(estado ->
                        Assertions.assertTrue(
                                List.of("enviado", "fallido").contains(estado),
                                "estado debe ser enviado o fallido, fue: " + estado));
    }

    // ── TC-MSG-006 — No reenviar mensaje ya enviado ───────────────────────────

    @Test
    @DisplayName("POST /mensajes/reenviar/{id} con mensaje enviado retorna 422")
    void noReenviarMensajeEnviado() {
        Integer idCliente = insertarClienteCore(COMPANIA, SUCURSAL);
        Integer plt = insertarPlantilla(COMPANIA, SUCURSAL, "ausencia_2d", "P", "Texto");
        Long idMsg = insertarMensajeLog(COMPANIA, SUCURSAL, idCliente, plt,
                "ausencia_2d", "whatsapp", "Texto Test", "enviado");

        webTestClient.post()
                .uri("/api/v1/mensajes/reenviar/{id}", idMsg)
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(COMPANIA)))
                .exchange()
                .expectStatus().isEqualTo(422);
    }

    // ── TC-MSG-007 — Reenviar mensaje inexistente retorna 404 ─────────────────

    @Test
    @DisplayName("POST /mensajes/reenviar/99999 retorna 404")
    void reenviarInexistenteRetorna404() {
        webTestClient.post()
                .uri("/api/v1/mensajes/reenviar/99999")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtDueno(COMPANIA)))
                .exchange()
                .expectStatus().isNotFound();
    }

    // ── TC-MSG-008 — Cliente no puede acceder a mensajes ─────────────────────

    @Test
    @DisplayName("GET /mensajes con JWT cliente retorna 403")
    void clienteNoPuedeVerMensajes() {
        webTestClient.get()
                .uri("/api/v1/mensajes")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtCliente(5, COMPANIA)))
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── TC-MSG-009 — Validación request enviar ────────────────────────────────

    @Test
    @DisplayName("POST /mensajes/enviar sin campos obligatorios retorna 400")
    void enviarSinCamposRetorna400() {
        webTestClient.post()
                .uri("/api/v1/mensajes/enviar")
                .header(HttpHeaders.AUTHORIZATION, bearerHeader(jwtRecepcion(COMPANIA)))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("canal", "whatsapp"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    // ── TC-MSG-ANTISPAM-001 — Anti-spam: no reenviar mismo ciclo ─────────────

    @Test
    @DisplayName("countByClienteAndTipoDesde retorna 0 cuando no hay mensajes previos")
    void antiSpamSinMensajesPrevios() {
        Long count = databaseClient.sql("""
                SELECT COUNT(*) FROM asistencia.mensajes_log
                WHERE id_cliente = 999999
                  AND tipo = 'ausencia_2d'
                  AND fecha_programada >= NOW() - INTERVAL '30 days'
                  AND eliminado = false
                """)
                .map(row -> row.get(0, Long.class))
                .one()
                .block();

        Assertions.assertEquals(0L, count);
    }

    @Test
    @DisplayName("countByClienteAndTipoDesde detecta mensaje ya enviado en el ciclo")
    void antiSpamDetectaMensajePrevio() {
        Integer idCliente = insertarClienteCore(COMPANIA, SUCURSAL);
        Integer plt = insertarPlantilla(COMPANIA, SUCURSAL, "ausencia_2d", "P", "C");
        insertarMensajeLog(COMPANIA, SUCURSAL, idCliente, plt, "ausencia_2d", "whatsapp", "C", "enviado");

        Long count = databaseClient.sql("""
                SELECT COUNT(*) FROM asistencia.mensajes_log
                WHERE id_cliente = :cli
                  AND tipo = 'ausencia_2d'
                  AND fecha_programada >= :desde
                  AND eliminado = false
                """)
                .bind("cli", idCliente)
                .bind("desde", OffsetDateTime.now(ZoneOffset.UTC).minusDays(1))
                .map(row -> row.get(0, Long.class))
                .one()
                .block();

        Assertions.assertTrue(count > 0);
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private Long insertarMensajeLog(Integer idCompania, Integer idSucursal, Integer idCliente,
                                     Integer idPlantilla, String tipo, String canal,
                                     String contenido, String estado) {
        return databaseClient.sql("""
                INSERT INTO asistencia.mensajes_log
                  (id_compania, id_sucursal, id_cliente, id_plantilla,
                   tipo, canal, contenido, estado, fecha_programada, creacion_usuario)
                VALUES (:comp, :suc, :cli, :plt,
                        :tipo, :canal, :contenido, :estado, NOW(), 'test')
                RETURNING id
                """)
                .bind("comp", idCompania).bind("suc", idSucursal)
                .bind("cli", idCliente).bind("plt", idPlantilla)
                .bind("tipo", tipo).bind("canal", canal)
                .bind("contenido", contenido).bind("estado", estado)
                .map(row -> row.get("id", Long.class))
                .one()
                .block();
    }
}
