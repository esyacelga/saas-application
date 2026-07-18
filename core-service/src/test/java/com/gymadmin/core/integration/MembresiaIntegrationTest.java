package com.gymadmin.core.integration;

import com.gymadmin.core.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("Membresías — integración")
class MembresiaIntegrationTest extends BaseIntegrationTest {

    // ── GET /clientes/{id}/membresias ────────────────────────────────────────

    @Test
    @DisplayName("GET /clientes/{id}/membresias devuelve historial de membresías")
    void historialMembresias() {
        Long idPersona = seedPersona("TEST-MEM-001", "Luis Torres");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoCalendario("Mensual Hist");
        seedMembresia(idCliente, idTipo, "2026-04-01", "2026-04-30");

        get("/api/v1/clientes/" + idCliente + "/membresias", jwtAdminCompania(TEST_COMPANIA))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$[0].id_cliente").isEqualTo(idCliente.intValue())
                .jsonPath("$[0].estado").isEqualTo("activa");
    }

    // ── POST /clientes/{id}/membresias ───────────────────────────────────────

    @Test
    @DisplayName("POST /clientes/{id}/membresias vende membresía calendario y calcula fecha_fin")
    void venderMembresiaCalendario() {
        Long idPersona = seedPersona("TEST-MEM-002", "Elena Salas");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoCalendario("Mensual Venta");

        String hoy = LocalDate.now().toString();

        post("/api/v1/clientes/" + idCliente + "/membresias",
                jwtRecepcion(TEST_COMPANIA), Map.of(
                        "id_tipo_membresia", idTipo,
                        "fecha_inicio", hoy,
                        "descuento_aplicado", 0
                ))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isNumber()
                .jsonPath("$.id_cliente").isEqualTo(idCliente.intValue())
                .jsonPath("$.estado").isEqualTo("activa")
                .jsonPath("$.precio_pagado").isEqualTo(35.0)
                .jsonPath("$.fecha_fin").isEqualTo(LocalDate.now().plusMonths(1).toString());
    }

    @Test
    @DisplayName("POST /clientes/{id}/membresias vende membresía accesos y copia dias_acceso_total")
    void venderMembresiaAccesos() {
        Long idPersona = seedPersona("TEST-MEM-003", "Andrés Cano");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoAccesos("Tarjeta 22 Venta", 22);

        String hoy = LocalDate.now().toString();

        post("/api/v1/clientes/" + idCliente + "/membresias",
                jwtRecepcion(TEST_COMPANIA), Map.of(
                        "id_tipo_membresia", idTipo,
                        "fecha_inicio", hoy,
                        "descuento_aplicado", 0
                ))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.dias_acceso_total").isEqualTo(22)
                .jsonPath("$.estado").isEqualTo("activa");
    }

    // ── GET /membresias/{id} ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /membresias/{id} devuelve detalle de membresía calendario")
    void detalleMembresia() {
        Long idPersona = seedPersona("TEST-MEM-004", "Camila Ruiz");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoCalendario("Mensual Det");
        Long idMem     = seedMembresia(idCliente, idTipo,
                LocalDate.now().toString(),
                LocalDate.now().plusMonths(1).toString());

        get("/api/v1/membresias/" + idMem, jwtAdminCompania(TEST_COMPANIA))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(idMem.intValue())
                .jsonPath("$.tipo").isEqualTo("Mensual Det")
                .jsonPath("$.modo_control").isEqualTo("calendario")
                .jsonPath("$.estado").isEqualTo("activa");
    }

    @Test
    @DisplayName("GET /membresias/{id} devuelve detalle con conteo accesos en tiempo real")
    void detalleMembresiaAccesos() {
        Long idPersona = seedPersona("TEST-MEM-005", "Diego Paz");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoAccesos("Tarjeta Det", 22);
        Long idMem     = seedMembresiaAccesos(idCliente, idTipo, 22);

        get("/api/v1/membresias/" + idMem, jwtAdminCompania(TEST_COMPANIA))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.modo_control").isEqualTo("accesos")
                .jsonPath("$.dias_acceso_total").isEqualTo(22)
                .jsonPath("$.dias_acceso_usados").isEqualTo(0)
                .jsonPath("$.dias_acceso_restantes").isEqualTo(22);
    }

    // ── PUT /membresias/{id}/anular ──────────────────────────────────────────

    @Test
    @DisplayName("PUT /membresias/{id}/anular cambia estado a anulada")
    void anularMembresia() {
        Long idPersona = seedPersona("TEST-MEM-006", "Natalia Cruz");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoCalendario("Mensual Anular");
        Long idMem     = seedMembresia(idCliente, idTipo,
                LocalDate.now().toString(),
                LocalDate.now().plusMonths(1).toString());

        put("/api/v1/membresias/" + idMem + "/anular",
                jwtAdminCompania(TEST_COMPANIA), Map.of("motivo", "Solicitud del cliente"))
                .expectStatus().isOk();
    }

    // ── GET /membresias/validar-acceso ───────────────────────────────────────

    @Test
    @DisplayName("GET /membresias/validar-acceso devuelve permitido=true para membresía vigente")
    void validarAccesoPermitido() {
        Long idPersona = seedPersona("TEST-MEM-007", "Roberto Lara");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoCalendario("Mensual Val");
        seedMembresia(idCliente, idTipo,
                LocalDate.now().toString(),
                LocalDate.now().plusMonths(1).toString());

        webTestClient.get()
                .uri("/api/v1/membresias/validar-acceso?id_persona=" + idPersona + "&id_compania=" + TEST_COMPANIA)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.permitido").isEqualTo(true)
                .jsonPath("$.id_membresia").isNumber()
                .jsonPath("$.modo_control").isEqualTo("calendario");
    }

    @Test
    @DisplayName("GET /membresias/validar-acceso devuelve permitido=false sin membresía")
    void validarAccesoSinMembresia() {
        webTestClient.get()
                .uri("/api/v1/membresias/validar-acceso?id_persona=999999&id_compania=" + TEST_COMPANIA)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.permitido").isEqualTo(false)
                .jsonPath("$.razon").isEqualTo("sin_membresia");
    }

    @Test
    @DisplayName("GET /membresias/validar-acceso devuelve membresia_vencida cuando fecha_fin está en el pasado")
    void validarAccesoMembresiaVencida() {
        Long idPersona = seedPersona("TEST-MEM-008", "Carlos Mendez");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoCalendario("Mensual Vencida");
        // estado=activa para que la query la encuentre; fecha_fin pasada para disparar la regla de vencimiento
        seedMembresia(idCliente, idTipo, "2025-01-01", "2025-01-31");

        webTestClient.get()
                .uri("/api/v1/membresias/validar-acceso?id_persona=" + idPersona + "&id_compania=" + TEST_COMPANIA)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.permitido").isEqualTo(false)
                .jsonPath("$.razon").isEqualTo("membresia_vencida");
    }

    @Test
    @DisplayName("GET /membresias/validar-acceso devuelve accesos_agotados cuando dias_acceso_total es 0")
    void validarAccesoAccesosAgotados() {
        Long idPersona = seedPersona("TEST-MEM-009", "Ana Guerrero");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoAccesos("Tarjeta Agotada", 10);
        // 0 accesos totales → usados(0) >= total(0) → agotados
        seedMembresiaAccesos(idCliente, idTipo, 0);

        webTestClient.get()
                .uri("/api/v1/membresias/validar-acceso?id_persona=" + idPersona + "&id_compania=" + TEST_COMPANIA)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.permitido").isEqualTo(false)
                .jsonPath("$.razon").isEqualTo("accesos_agotados");
    }

    @Test
    @DisplayName("POST /clientes/{id}/membresias devuelve 409 si el cliente ya tiene una membresía activa")
    void venderConMembresiaActivaDevuelveConflicto() {
        Long idPersona = seedPersona("TEST-MEM-010", "Pedro Vargas");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoCalendario("Mensual Conflicto");
        seedMembresia(idCliente, idTipo,
                LocalDate.now().toString(),
                LocalDate.now().plusMonths(1).toString());

        post("/api/v1/clientes/" + idCliente + "/membresias",
                jwtRecepcion(TEST_COMPANIA), Map.of(
                        "id_tipo_membresia", idTipo,
                        "fecha_inicio", LocalDate.now().toString(),
                        "descuento_aplicado", 0
                ))
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.message").isEqualTo("El cliente ya tiene una membresía activa");
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  GYM-003 — Estado de pago en ventas de membresía
    // ═════════════════════════════════════════════════════════════════════════

    // ── §4.4  POST /clientes/{id}/membresias con estado_pago ────────────────

    // TC-GYM-003-01
    @Test
    @DisplayName("POST con estado_pago=PENDIENTE crea membresía sin fechas y responde 201")
    void venderPendienteCreaSinFechas() {
        Long idPersona = seedPersona("TEST-MEM-101", "Ivana Peralta");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoCalendario("Mensual Pendiente");

        post("/api/v1/clientes/" + idCliente + "/membresias",
                jwtRecepcion(TEST_COMPANIA), Map.of(
                        "id_tipo_membresia", idTipo,
                        "fecha_inicio", LocalDate.now().toString(),
                        "descuento_aplicado", 0,
                        "estado_pago", "PENDIENTE"
                ))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id").isNumber()
                .jsonPath("$.id_cliente").isEqualTo(idCliente.intValue())
                .jsonPath("$.estado").isEqualTo("activa")
                .jsonPath("$.estado_pago").isEqualTo("PENDIENTE")
                .jsonPath("$.fecha_inicio").isEmpty()
                .jsonPath("$.fecha_fin").isEmpty();
    }

    // TC-GYM-003-02
    @Test
    @DisplayName("POST con estado_pago=PENDIENTE devuelve 409 si ya existe otra PENDIENTE viva")
    void venderPendienteConOtraPendienteDevuelveConflicto() {
        Long idPersona = seedPersona("TEST-MEM-102", "Simón Rojas");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoCalendario("Mensual PendienteX2");
        seedMembresiaPendiente(idCliente, idTipo);

        post("/api/v1/clientes/" + idCliente + "/membresias",
                jwtRecepcion(TEST_COMPANIA), Map.of(
                        "id_tipo_membresia", idTipo,
                        "fecha_inicio", LocalDate.now().toString(),
                        "descuento_aplicado", 0,
                        "estado_pago", "PENDIENTE"
                ))
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.message").isEqualTo("El cliente ya tiene una membresía pendiente de pago");
    }

    // TC-GYM-003-03 — §N8: PAGADA activa + PENDIENTE nueva convive (renovación anticipada)
    @Test
    @DisplayName("POST con estado_pago=PENDIENTE convive con una PAGADA activa (renovación anticipada)")
    void venderPendienteConvivetConPagadaActiva() {
        Long idPersona = seedPersona("TEST-MEM-103", "Julia Herrera");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoCalendario("Mensual RenovAntic");
        seedMembresia(idCliente, idTipo,
                LocalDate.now().toString(),
                LocalDate.now().plusMonths(1).toString());

        post("/api/v1/clientes/" + idCliente + "/membresias",
                jwtRecepcion(TEST_COMPANIA), Map.of(
                        "id_tipo_membresia", idTipo,
                        "fecha_inicio", LocalDate.now().toString(),
                        "descuento_aplicado", 0,
                        "estado_pago", "PENDIENTE"
                ))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.estado_pago").isEqualTo("PENDIENTE")
                .jsonPath("$.fecha_inicio").isEmpty()
                .jsonPath("$.fecha_fin").isEmpty();
    }

    // TC-GYM-003-04 — regresión: sin estado_pago sigue creando PAGADA con fechas
    @Test
    @DisplayName("POST sin estado_pago crea membresía PAGADA con fechas (default)")
    void venderSinEstadoPagoCreaPagadaConFechas() {
        Long idPersona = seedPersona("TEST-MEM-104", "Rafael Ortega");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoCalendario("Mensual DefaultPagado");

        String hoy = LocalDate.now().toString();

        post("/api/v1/clientes/" + idCliente + "/membresias",
                jwtRecepcion(TEST_COMPANIA), Map.of(
                        "id_tipo_membresia", idTipo,
                        "fecha_inicio", hoy,
                        "descuento_aplicado", 0
                ))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.estado_pago").isEqualTo("PAGADO")
                .jsonPath("$.fecha_inicio").isEqualTo(hoy)
                .jsonPath("$.fecha_fin").isEqualTo(LocalDate.now().plusMonths(1).toString());
    }

    // ── §4.6  POST /membresias/{id}/confirmar-pago ───────────────────────────

    // TC-GYM-003-05
    @Test
    @DisplayName("POST /membresias/{id}/confirmar-pago transiciona PENDIENTE→PAGADO y calcula fechas")
    void confirmarPagoTransicionaYCalculaFechas() {
        Long idPersona = seedPersona("TEST-MEM-105", "Belén Castillo");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoCalendario("Mensual ConfirmarPago");
        Long idMem     = seedMembresiaPendiente(idCliente, idTipo);

        post("/api/v1/membresias/" + idMem + "/confirmar-pago",
                jwtRecepcion(TEST_COMPANIA), Map.of())
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(idMem.intValue())
                .jsonPath("$.estado_pago").isEqualTo("PAGADO")
                .jsonPath("$.fecha_inicio").isEqualTo(LocalDate.now().toString())
                .jsonPath("$.fecha_fin").isEqualTo(LocalDate.now().plusMonths(1).toString());
    }

    // TC-GYM-003-06 — idempotencia
    @Test
    @DisplayName("POST /membresias/{id}/confirmar-pago sobre PAGADA es idempotente (200 sin recalcular fechas)")
    void confirmarPagoIdempotenteSobrePagada() {
        Long idPersona = seedPersona("TEST-MEM-106", "Diego Salgado");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoCalendario("Mensual Idempotente");
        String fechaInicioOriginal = "2026-05-01";
        String fechaFinOriginal    = "2026-06-01";
        Long idMem     = seedMembresia(idCliente, idTipo, fechaInicioOriginal, fechaFinOriginal);

        post("/api/v1/membresias/" + idMem + "/confirmar-pago",
                jwtRecepcion(TEST_COMPANIA), Map.of())
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(idMem.intValue())
                .jsonPath("$.estado_pago").isEqualTo("PAGADO")
                // no debe re-calcular: mantiene las fechas originales
                .jsonPath("$.fecha_inicio").isEqualTo(fechaInicioOriginal)
                .jsonPath("$.fecha_fin").isEqualTo(fechaFinOriginal);
    }

    // TC-GYM-003-07
    @Test
    @DisplayName("POST /membresias/{id}/confirmar-pago sobre una eliminada devuelve 409")
    void confirmarPagoSobreEliminadaDevuelveConflicto() {
        Long idPersona = seedPersona("TEST-MEM-107", "Camilo Ríos");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoCalendario("Mensual EliminadaConfirmar");
        Long idMem     = seedMembresiaRechazada(idCliente, idTipo, "SOCIO_CAMBIO_OPINION");

        post("/api/v1/membresias/" + idMem + "/confirmar-pago",
                jwtRecepcion(TEST_COMPANIA), Map.of())
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.message").isEqualTo("La membresía fue rechazada y no puede confirmarse");
    }

    // TC-GYM-003-08 — 403 sin permiso granular
    @Test
    @DisplayName("POST /membresias/{id}/confirmar-pago sin permiso 'membresias:confirmar_pago' devuelve 403")
    void confirmarPagoSinPermisoDevuelve403() {
        Long idPersona = seedPersona("TEST-MEM-108", "Nadia Ponce");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoCalendario("Mensual SinPermiso");
        Long idMem     = seedMembresiaPendiente(idCliente, idTipo);

        String jwt = jwtRecepcionConPermisos(TEST_COMPANIA, List.of("membresias:leer"));

        post("/api/v1/membresias/" + idMem + "/confirmar-pago", jwt, Map.of())
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.message").isEqualTo("Missing permission: membresias:confirmar_pago");
    }

    // ── §4.7  POST /membresias/{id}/rechazar ─────────────────────────────────

    // TC-GYM-003-09
    @Test
    @DisplayName("POST /membresias/{id}/rechazar sobre PENDIENTE marca eliminado y setea auditoría")
    void rechazarPendienteMarcaEliminadoYAuditoria() {
        Long idPersona = seedPersona("TEST-MEM-109", "Óscar Vera");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoCalendario("Mensual Rechazar");
        Long idMem     = seedMembresiaPendiente(idCliente, idTipo);

        post("/api/v1/membresias/" + idMem + "/rechazar",
                jwtRecepcion(TEST_COMPANIA),
                Map.of("motivo_eliminacion", "SOCIO_CAMBIO_OPINION"))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(idMem.intValue())
                .jsonPath("$.eliminado").isEqualTo(true)
                .jsonPath("$.motivo_eliminacion").isEqualTo("SOCIO_CAMBIO_OPINION");

        // Verificación en BD de fecha_eliminacion y eliminado_por
        Map<String, Object> row = databaseClient.sql(
                "SELECT fecha_eliminacion, eliminado_por FROM core.membresias WHERE id = :id")
                .bind("id", idMem)
                .fetch()
                .one()
                .block();
        assertNotNull(row);
        assertNotNull(row.get("fecha_eliminacion"), "fecha_eliminacion debe ser seteada");
        Object eliminadoPor = row.get("eliminado_por");
        assertNotNull(eliminadoPor, "eliminado_por debe ser seteado");
        // El JWT de jwtRecepcion() usa subject "3" → principal.userId = "3"
        assertEquals(3, ((Number) eliminadoPor).intValue(),
                "eliminado_por debe coincidir con principal.userId (3)");
    }

    // TC-GYM-003-10
    @Test
    @DisplayName("POST /membresias/{id}/rechazar sobre PAGADA devuelve 409")
    void rechazarPagadaDevuelveConflicto() {
        Long idPersona = seedPersona("TEST-MEM-110", "Ivo Rendón");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoCalendario("Mensual RechazarPagada");
        Long idMem     = seedMembresia(idCliente, idTipo,
                LocalDate.now().toString(),
                LocalDate.now().plusMonths(1).toString());

        post("/api/v1/membresias/" + idMem + "/rechazar",
                jwtRecepcion(TEST_COMPANIA),
                Map.of("motivo_eliminacion", "ERROR_DE_VENTA"))
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.message")
                .isEqualTo("No se puede rechazar una membresía pagada; usar anulación");
    }

    // TC-GYM-003-11
    @Test
    @DisplayName("POST /membresias/{id}/rechazar sobre ya eliminada devuelve 409")
    void rechazarEliminadaDevuelveConflicto() {
        Long idPersona = seedPersona("TEST-MEM-111", "Mila Sarmiento");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoCalendario("Mensual DobleRechazo");
        Long idMem     = seedMembresiaRechazada(idCliente, idTipo, "DUPLICADA");

        post("/api/v1/membresias/" + idMem + "/rechazar",
                jwtRecepcion(TEST_COMPANIA),
                Map.of("motivo_eliminacion", "OTRO"))
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.message").isEqualTo("La membresía ya fue rechazada");
    }

    // TC-GYM-003-12
    @Test
    @DisplayName("POST /membresias/{id}/rechazar con motivo fuera del catálogo devuelve 400")
    void rechazarConMotivoInvalidoDevuelve400() {
        Long idPersona = seedPersona("TEST-MEM-112", "Ariel Pinto");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoCalendario("Mensual MotivoInvalido");
        Long idMem     = seedMembresiaPendiente(idCliente, idTipo);

        post("/api/v1/membresias/" + idMem + "/rechazar",
                jwtRecepcion(TEST_COMPANIA),
                Map.of("motivo_eliminacion", "MOTIVO_INEXISTENTE"))
                .expectStatus().isBadRequest();
    }

    // TC-GYM-003-13
    @Test
    @DisplayName("POST /membresias/{id}/rechazar sin body devuelve 400")
    void rechazarSinBodyDevuelve400() {
        Long idPersona = seedPersona("TEST-MEM-113", "Rocío Torres");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoCalendario("Mensual RechazarSinBody");
        Long idMem     = seedMembresiaPendiente(idCliente, idTipo);

        // Sin body: motivo_eliminacion es null → @NotNull dispara 400.
        post("/api/v1/membresias/" + idMem + "/rechazar",
                jwtRecepcion(TEST_COMPANIA),
                Map.of())
                .expectStatus().isBadRequest();
    }

    // ── §4.8  GET /companias/{idCompania}/membresias/pendientes ──────────────

    // TC-GYM-003-14
    @Test
    @DisplayName("GET /companias/{idCompania}/membresias/pendientes lista PENDIENTES vivas ordenadas por creacion_fecha DESC")
    void listarPendientesRetornaVivasOrdenadas() {
        Long idPersona1 = seedPersona("TEST-MEM-114", "Fer Andrade");
        Long idCliente1 = seedCliente(idPersona1);
        Long idPersona2 = seedPersona("TEST-MEM-115", "Sofía Morales");
        Long idCliente2 = seedCliente(idPersona2);
        Long idTipo     = seedTipoCalendario("Mensual ListPendientes");

        // Fijamos creacion_fecha explícitamente para que el orden DESC sea determinístico.
        Long idMemVieja = databaseClient.sql(
                "INSERT INTO core.membresias(id_compania, id_sucursal, id_cliente, id_tipo_membresia, " +
                "precio_pagado, descuento_aplicado, estado, estado_pago, creacion_usuario, creacion_fecha) " +
                "VALUES (:comp, :suc, :cli, :tipo, 35.00, 0, 'activa', 'PENDIENTE', 'test', " +
                "NOW() - INTERVAL '1 hour') RETURNING id")
                .bind("comp", TEST_COMPANIA).bind("suc", TEST_SUCURSAL)
                .bind("cli", idCliente1).bind("tipo", idTipo)
                .map(row -> row.get("id", Long.class)).one().block();
        Long idMemNueva = seedMembresiaPendiente(idCliente2, idTipo);

        get("/api/v1/companias/" + TEST_COMPANIA + "/membresias/pendientes",
                jwtRecepcion(TEST_COMPANIA))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()").isEqualTo(2)
                // orden DESC por creacion_fecha → la nueva aparece primero
                .jsonPath("$[0].id").isEqualTo(idMemNueva.intValue())
                .jsonPath("$[1].id").isEqualTo(idMemVieja.intValue())
                .jsonPath("$[0].tipo_nombre").isEqualTo("Mensual ListPendientes")
                .jsonPath("$[0].modo_control").isEqualTo("calendario")
                // cada fila trae su propio nombre_cliente (§4.8 — HU GYM-003)
                .jsonPath("$[0].nombre_cliente").isEqualTo("Sofía Morales")
                .jsonPath("$[1].nombre_cliente").isEqualTo("Fer Andrade");
    }

    // TC-GYM-003-15 — regresión negativa: no incluye PAGADAS ni PENDIENTES eliminadas
    @Test
    @DisplayName("GET /companias/{idCompania}/membresias/pendientes excluye PAGADAS y PENDIENTES eliminadas")
    void listarPendientesExcluyePagadasYEliminadas() {
        Long idPersona1 = seedPersona("TEST-MEM-116", "León Cortés");
        Long idCliente1 = seedCliente(idPersona1);
        Long idPersona2 = seedPersona("TEST-MEM-117", "Malena Ríos");
        Long idCliente2 = seedCliente(idPersona2);
        Long idPersona3 = seedPersona("TEST-MEM-118", "Bruno Gil");
        Long idCliente3 = seedCliente(idPersona3);
        Long idTipo     = seedTipoCalendario("Mensual FiltroPendientes");

        // PAGADA activa
        seedMembresia(idCliente1, idTipo,
                LocalDate.now().toString(),
                LocalDate.now().plusMonths(1).toString());
        // PENDIENTE eliminada
        seedMembresiaRechazada(idCliente2, idTipo, "ERROR_DE_VENTA");
        // PENDIENTE viva (única que debe aparecer)
        Long idMemPendiente = seedMembresiaPendiente(idCliente3, idTipo);

        get("/api/v1/companias/" + TEST_COMPANIA + "/membresias/pendientes",
                jwtRecepcion(TEST_COMPANIA))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].id").isEqualTo(idMemPendiente.intValue())
                .jsonPath("$[0].nombre_cliente").isEqualTo("Bruno Gil");
    }

    // TC-GYM-003-15b — regresión defensiva: cliente huérfano (persona borrada) no rompe el listado
    @Test
    @DisplayName("GET /companias/{idCompania}/membresias/pendientes devuelve nombre_cliente=null si la persona no existe")
    void listarPendientesConPersonaAusenteDevuelveNombreNulo() {
        Long idPersona = seedPersona("TEST-MEM-118B", "Persona Efímera");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoCalendario("Mensual PersonaAusente");
        Long idMem     = seedMembresiaPendiente(idCliente, idTipo);

        // Soft-delete de la persona simula un huérfano: la membresía existe, el cliente
        // apunta a una persona con eliminado=true → findNombreById devuelve empty.
        databaseClient.sql("UPDATE identidad.personas SET eliminado = true WHERE id = :id")
                .bind("id", idPersona)
                .then()
                .block();

        get("/api/v1/companias/" + TEST_COMPANIA + "/membresias/pendientes",
                jwtRecepcion(TEST_COMPANIA))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$").isArray()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].id").isEqualTo(idMem.intValue())
                .jsonPath("$[0].nombre_cliente").isEmpty();
    }

    // TC-GYM-003-16 — cross-tenant guard
    @Test
    @DisplayName("GET /companias/{idCompania}/membresias/pendientes con otra compañía devuelve 403")
    void listarPendientesCrossTenantDevuelve403() {
        long otraCompania = TEST_COMPANIA + 999L;
        get("/api/v1/companias/" + otraCompania + "/membresias/pendientes",
                jwtRecepcion(TEST_COMPANIA))
                .expectStatus().isForbidden();
    }

    // ── §4.5  GET /membresias/validar-acceso con nuevos códigos ─────────────

    // TC-GYM-003-17
    @Test
    @DisplayName("GET /membresias/validar-acceso con PENDIENTE viva devuelve razon=pago_pendiente")
    void validarAccesoPendienteDevuelvePagoPendiente() {
        Long idPersona = seedPersona("TEST-MEM-119", "Vero Aguilar");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoCalendario("Mensual ValPendiente");
        seedMembresiaPendiente(idCliente, idTipo);

        webTestClient.get()
                .uri("/api/v1/membresias/validar-acceso?id_persona=" + idPersona
                        + "&id_compania=" + TEST_COMPANIA)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.permitido").isEqualTo(false)
                .jsonPath("$.razon").isEqualTo("pago_pendiente");
    }

    // TC-GYM-003-18
    @Test
    @DisplayName("GET /membresias/validar-acceso con membresía eliminada devuelve razon=membresia_rechazada")
    void validarAccesoEliminadaDevuelveMembresiaRechazada() {
        Long idPersona = seedPersona("TEST-MEM-120", "Kevin Lozano");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoCalendario("Mensual ValRechazada");
        seedMembresiaRechazada(idCliente, idTipo, "DATOS_INCORRECTOS");

        webTestClient.get()
                .uri("/api/v1/membresias/validar-acceso?id_persona=" + idPersona
                        + "&id_compania=" + TEST_COMPANIA)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.permitido").isEqualTo(false)
                .jsonPath("$.razon").isEqualTo("membresia_rechazada");
    }

    // TC-GYM-003-19 — regresión positiva
    @Test
    @DisplayName("GET /membresias/validar-acceso con PAGADA vigente devuelve permitido=true")
    void validarAccesoPagadaVigentePermitido() {
        Long idPersona = seedPersona("TEST-MEM-121", "Renata Ordóñez");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoCalendario("Mensual ValPagada");
        seedMembresia(idCliente, idTipo,
                LocalDate.now().toString(),
                LocalDate.now().plusMonths(1).toString());

        webTestClient.get()
                .uri("/api/v1/membresias/validar-acceso?id_persona=" + idPersona
                        + "&id_compania=" + TEST_COMPANIA)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.permitido").isEqualTo(true)
                .jsonPath("$.id_membresia").isNumber()
                .jsonPath("$.modo_control").isEqualTo("calendario");
    }

    // TC-GYM-003-20 — orden §4.5: pago_pendiente antes que vencida
    @Test
    @DisplayName("GET /membresias/validar-acceso con PENDIENTE viva + PAGADA vencida evalúa pago_pendiente primero")
    void validarAccesoPendienteTienePrioridadSobreVencida() {
        Long idPersona = seedPersona("TEST-MEM-122", "Xavier Pineda");
        Long idCliente = seedCliente(idPersona);
        Long idTipo    = seedTipoCalendario("Mensual OrdenEval");

        // PAGADA vencida (pero la query findActivaByIdClienteAndIdCompania solo mira
        // estado='activa'; seedMembresia inserta con estado='activa' aunque fecha_fin sea pasada,
        // por lo que la rama "vencida" se evaluaría si no hubiera PENDIENTE.
        seedMembresia(idCliente, idTipo, "2025-01-01", "2025-01-31");
        // PENDIENTE viva
        seedMembresiaPendiente(idCliente, idTipo);

        webTestClient.get()
                .uri("/api/v1/membresias/validar-acceso?id_persona=" + idPersona
                        + "&id_compania=" + TEST_COMPANIA)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.permitido").isEqualTo(false)
                .jsonPath("$.razon").isEqualTo("pago_pendiente");
    }
}
