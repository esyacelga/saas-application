package com.gymadmin.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.PermisoEntity;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.RolEntity;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.repository.PermisoR2dbcRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.r2dbc.core.DatabaseClient;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PermisoIT extends IntegrationTestBase {

    @Autowired
    private PermisoR2dbcRepository permisoRepo;

    @Autowired
    private DatabaseClient db;

    private Integer savedPermisoId;
    private Integer savedRolId;

    @BeforeEach
    void seedTestData() {
        PermisoEntity permiso = permisoRepo.save(PermisoEntity.builder()
                .idCompania(ID_COMPANIA)
                .idSucursal(ID_SUCURSAL)
                .nombre("socios:leer")
                .modulo("socios")
                .descripcion("Ver socios")
                .creacionFecha(OffsetDateTime.now())
                .creacionUsuario("test")
                .build()).block();
        savedPermisoId = permiso.getId();

        RolEntity rol = rolRepo.save(RolEntity.builder()
                .idCompania(ID_COMPANIA)
                .idSucursal(ID_SUCURSAL)
                .nombre("Rol Test Permiso")
                .creacionFecha(OffsetDateTime.now())
                .creacionUsuario("test")
                .build()).block();
        savedRolId = rol.getId();

        db.sql("""
                INSERT INTO seguridad.rol_permisos (id_rol, id_permiso, creacion_fecha, creacion_usuario)
                VALUES (:idRol, :idPermiso, :fecha, :usuario)
                """)
                .bind("idRol", savedRolId)
                .bind("idPermiso", savedPermisoId)
                .bind("fecha", OffsetDateTime.now())
                .bind("usuario", "test")
                .fetch().rowsUpdated().block();
    }

    @AfterEach
    void cleanup() {
        if (savedRolId != null) {
            db.sql("DELETE FROM seguridad.rol_permisos WHERE id_rol = :idRol")
                    .bind("idRol", savedRolId)
                    .fetch().rowsUpdated().block();
            rolRepo.deleteById(savedRolId).block();
        }
        if (savedPermisoId != null) {
            permisoRepo.deleteById(savedPermisoId).block();
        }
    }

    @Test
    void listarPermisos_debeRetornarPermisosDeCompania() {
        webClient.get().uri("/api/v1/permisos")
                .header("Authorization", staffBearer("roles:leer"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .consumeWith(result -> {
                    JsonNode body = result.getResponseBody();
                    assertNotNull(body);
                    assertTrue(body.isArray(), "La respuesta debe ser un arreglo");

                    JsonNode permisoNode = null;
                    for (JsonNode node : body) {
                        if (savedPermisoId.equals(node.get("id").asInt())) {
                            permisoNode = node;
                            break;
                        }
                    }
                    assertNotNull(permisoNode, "El permiso creado debe aparecer en la lista");
                    assertEquals("socios:leer", permisoNode.get("nombre").asText());
                    assertEquals("socios", permisoNode.get("modulo").asText());
                });
    }

    @Test
    void listarPermisosPorRol_debeRetornarPermisosDelRol() {
        webClient.get().uri("/api/v1/permisos/by-rol/" + savedRolId)
                .header("Authorization", staffBearer("roles:leer"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .consumeWith(result -> {
                    JsonNode body = result.getResponseBody();
                    assertNotNull(body);
                    assertTrue(body.isArray(), "La respuesta debe ser un arreglo");
                    assertEquals(1, body.size(), "Debe haber exactamente un permiso asignado al rol");
                    assertEquals(savedPermisoId, body.get(0).get("id").asInt());
                    assertEquals("socios:leer", body.get(0).get("nombre").asText());
                    assertEquals("socios", body.get(0).get("modulo").asText());
                });
    }
}
