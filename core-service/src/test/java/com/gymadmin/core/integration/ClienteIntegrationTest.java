package com.gymadmin.core.integration;

import com.gymadmin.core.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

@DisplayName("Clientes — happy path")
class ClienteIntegrationTest extends BaseIntegrationTest {

    // ── GET /clientes ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /clientes devuelve lista paginada con total")
    void listarClientes() {
        Long idPersona = seedPersona("TEST-001", "Ana García");
        seedCliente(idPersona);

        get("/api/v1/clientes", jwtAdminCompania(TEST_COMPANIA))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.total").isEqualTo(1)
                .jsonPath("$.pagina").isEqualTo(1)
                .jsonPath("$.datos").isArray()
                .jsonPath("$.datos[0].id").isNumber();
    }

    // ── POST /clientes ───────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /clientes registra cliente con persona nueva y devuelve 201")
    void registrarClienteConPersonaNueva() {
        post("/api/v1/clientes", jwtRecepcion(TEST_COMPANIA), Map.of(
                "ci", "TEST-NEW-001",
                "nombre", "Carlos Pérez",
                "telefono", "0991234567",
                "correo", "carlos@test.com",
                "id_sucursal", TEST_SUCURSAL
        ))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id_cliente").isNumber()
                .jsonPath("$.id_persona").isNumber();
    }

    @Test
    @DisplayName("POST /clientes registra cliente con persona existente y reutiliza persona")
    void registrarClienteConPersonaExistente() {
        seedPersona("TEST-EXIST-001", "María López");

        post("/api/v1/clientes", jwtRecepcion(TEST_COMPANIA), Map.of(
                "ci", "TEST-EXIST-001",
                "nombre", "María López",
                "id_sucursal", TEST_SUCURSAL
        ))
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.id_cliente").isNumber()
                .jsonPath("$.id_persona").isNumber();
    }

    // ── GET /clientes/{id} ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /clientes/{id} devuelve ficha completa del cliente")
    void obtenerClientePorId() {
        Long idPersona = seedPersona("TEST-002", "Pedro Ramírez");
        Long idCliente = seedCliente(idPersona);

        get("/api/v1/clientes/" + idCliente, jwtAdminCompania(TEST_COMPANIA))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(idCliente.intValue())
                .jsonPath("$.estado").isEqualTo("activo");
    }

    // ── PUT /clientes/{id} ───────────────────────────────────────────────────

    @Test
    @DisplayName("PUT /clientes/{id} actualiza datos físicos del cliente")
    void actualizarCliente() {
        Long idPersona = seedPersona("TEST-003", "Sofía Vega");
        Long idCliente = seedCliente(idPersona);

        put("/api/v1/clientes/" + idCliente, jwtRecepcion(TEST_COMPANIA), Map.of(
                "peso_kg", 65.5,
                "altura_cm", 168.0,
                "objetivos", "Tonificar"
        ))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(idCliente.intValue());
    }

    // ── GET /clientes/ci/{ci} ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /clientes/ci/{ci} encuentra persona y reporta si es cliente del gym")
    void buscarPorCiClienteExistente() {
        Long idPersona = seedPersona("TEST-CI-001", "Lucas Mora");
        seedCliente(idPersona);

        get("/api/v1/clientes/ci/TEST-CI-001", jwtRecepcion(TEST_COMPANIA))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.persona.ci").isEqualTo("TEST-CI-001")
                .jsonPath("$.es_cliente_en_este_gym").isEqualTo(true)
                .jsonPath("$.id_cliente").isNumber();
    }

    @Test
    @DisplayName("GET /clientes/ci/{ci} encuentra persona que aún no es cliente del gym")
    void buscarPorCiPersonaNoCliente() {
        seedPersona("TEST-CI-002", "Valeria Ríos");

        get("/api/v1/clientes/ci/TEST-CI-002", jwtRecepcion(TEST_COMPANIA))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.persona.ci").isEqualTo("TEST-CI-002")
                .jsonPath("$.es_cliente_en_este_gym").isEqualTo(false);
    }
}
