package com.gymadmin.core.integration;

import com.gymadmin.core.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;

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
                .uri("/api/v1/membresias/validar-acceso?id_cliente=" + idCliente + "&id_compania=" + TEST_COMPANIA)
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
                .uri("/api/v1/membresias/validar-acceso?id_cliente=999999&id_compania=" + TEST_COMPANIA)
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
                .uri("/api/v1/membresias/validar-acceso?id_cliente=" + idCliente + "&id_compania=" + TEST_COMPANIA)
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
                .uri("/api/v1/membresias/validar-acceso?id_cliente=" + idCliente + "&id_compania=" + TEST_COMPANIA)
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
}
