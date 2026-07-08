package com.gymadmin.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.PersonaEntity;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.RolEntity;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.UsuarioStaffEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BitacoraIT extends IntegrationTestBase {

    @Autowired
    private DatabaseClient db;

    private Long savedBitacoraId;
    private Integer savedPersonaId;
    private Integer savedStaffId;
    private Integer savedRolId;

    @BeforeEach
    void seedTestData() {
        PersonaEntity persona = personaRepo.save(PersonaEntity.builder()
                .ci("CI-BIT-" + UUID.randomUUID().toString().substring(0, 8))
                .nombre("Staff Bitacora Test")
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block();
        savedPersonaId = persona.getId();

        RolEntity rol = rolRepo.save(RolEntity.builder()
                .idCompania(ID_COMPANIA).idSucursal(ID_SUCURSAL)
                .nombre("RolBitacoraIT-" + UUID.randomUUID().toString().substring(0, 6))
                .descripcion("Rol de prueba bitacora")
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block();
        savedRolId = rol.getId();

        UsuarioStaffEntity staff = staffRepo.save(UsuarioStaffEntity.builder()
                .idCompania(ID_COMPANIA).idSucursal(ID_SUCURSAL).idRol(savedRolId)
                .idPersona(savedPersonaId)
                .correo("bitacora-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com")
                .passwordHash(passwordEncoder.encode(TEST_PASSWORD))
                .activo(true)
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block();
        savedStaffId = staff.getId();

        savedBitacoraId = db.sql("""
                INSERT INTO seguridad.bitacora_accesos
                (id_compania, id_sucursal, id_usuario, modulo, accion, entidad_id, detalle, ip, fecha, creacion_fecha, creacion_usuario)
                VALUES (:idCompania, :idSucursal, :idUsuario, :modulo, :accion, :entidadId, :detalle::jsonb, :ip, :fecha, :fecha, 'test')
                RETURNING id
                """)
                .bind("idCompania", ID_COMPANIA)
                .bind("idSucursal", ID_SUCURSAL)
                .bind("idUsuario", savedStaffId)
                .bind("modulo", "socios")
                .bind("accion", "crear")
                .bind("entidadId", 1)
                .bind("detalle", "{}")
                .bind("ip", "127.0.0.1")
                .bind("fecha", OffsetDateTime.now())
                .map((row, meta) -> row.get("id", Long.class))
                .one().block();
    }

    @AfterEach
    void cleanup() {
        if (savedBitacoraId != null) {
            db.sql("DELETE FROM seguridad.bitacora_accesos WHERE id = :id")
                    .bind("id", savedBitacoraId)
                    .fetch().rowsUpdated().block();
        }
        if (savedStaffId != null) {
            staffRepo.deleteById(savedStaffId).block();
        }
        if (savedRolId != null) {
            rolRepo.deleteById(savedRolId).block();
        }
        if (savedPersonaId != null) {
            personaRepo.deleteById(savedPersonaId).block();
        }
    }

    @Test
    void listarBitacora_debeRetornarEntradasDeCompania() {
        webClient.get().uri("/api/v1/bitacora")
                .header("Authorization", staffBearer("usuarios:leer"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .consumeWith(result -> {
                    JsonNode body = result.getResponseBody();
                    assertNotNull(body);
                    assertTrue(body.get("total").asLong() >= 1, "El total debe ser al menos 1");

                    JsonNode datos = body.get("datos");
                    assertNotNull(datos, "La respuesta debe tener campo 'datos'");
                    assertTrue(datos.isArray(), "El campo 'datos' debe ser un arreglo");

                    JsonNode entrada = null;
                    for (JsonNode node : datos) {
                        if (savedBitacoraId.equals(node.get("id").asLong())) {
                            entrada = node;
                            break;
                        }
                    }
                    assertNotNull(entrada, "La entrada creada debe aparecer en la bitácora");
                    assertEquals("socios", entrada.get("modulo").asText());
                    assertEquals("crear", entrada.get("accion").asText());
                });
    }
}
