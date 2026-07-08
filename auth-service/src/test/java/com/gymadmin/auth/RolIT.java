package com.gymadmin.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.PermisoEntity;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.RolEntity;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.repository.PermisoR2dbcRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RolIT extends IntegrationTestBase {

    @Autowired
    private PermisoR2dbcRepository permisoRepo;

    @Autowired
    private DatabaseClient db;

    private Integer savedRolId;
    private Integer savedPermisoId;

    @AfterEach
    void cleanup() {
        if (savedRolId != null) {
            db.sql("DELETE FROM seguridad.rol_permisos WHERE id_rol = :id")
                    .bind("id", savedRolId).fetch().rowsUpdated().block();
            rolRepo.deleteById(savedRolId).block();
        }
        if (savedPermisoId != null) {
            permisoRepo.deleteById(savedPermisoId).block();
        }
    }

    @Test
    void crearRol_debeInsertarEnBd() {
        String nombre = "RolTest-" + UUID.randomUUID().toString().substring(0, 8);

        JsonNode response = webClient.post().uri("/api/v1/roles")
                .header("Authorization", staffBearer("roles:crear"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "nombre", nombre,
                        "descripcion", "Descripción de prueba"
                ))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();

        assertNotNull(response);
        savedRolId = response.get("id").asInt();
        assertTrue(savedRolId > 0, "La respuesta debe contener un ID válido");

        // Verifica que el rol fue insertado en seguridad.roles
        RolEntity inDb = rolRepo.findByIdAndIdCompania(savedRolId, ID_COMPANIA).block();
        assertNotNull(inDb, "El rol debe existir en la BD");
        assertEquals(nombre, inDb.getNombre());
        assertEquals("Descripción de prueba", inDb.getDescripcion());
        assertEquals(ID_COMPANIA, inDb.getIdCompania());
        assertEquals(ID_SUCURSAL, inDb.getIdSucursal());
        assertNotNull(inDb.getCreacionFecha(), "creacion_fecha debe estar registrado");
    }

    @Test
    void eliminarRol_debeBorrarDeBd() {
        // Insertar rol directamente en la BD
        RolEntity rol = rolRepo.save(RolEntity.builder()
                .idCompania(ID_COMPANIA).idSucursal(ID_SUCURSAL)
                .nombre("RolEliminar-" + UUID.randomUUID().toString().substring(0, 6))
                .descripcion("Rol para eliminar")
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block();
        savedRolId = rol.getId();

        webClient.delete().uri("/api/v1/roles/" + savedRolId)
                .header("Authorization", staffBearer("roles:crear"))
                .exchange()
                .expectStatus().isNoContent();

        // Verifica que el rol ya no existe en la base de datos
        RolEntity inDb = rolRepo.findByIdAndIdCompania(savedRolId, ID_COMPANIA).block();
        assertNull(inDb, "El rol debe haber sido eliminado de la BD");

        savedRolId = null; // ya fue eliminado, no limpiar en @AfterEach
    }

    @Test
    void listarRoles_debeRetornarRolesDeCompania() {
        // Insertar un rol para asegurar que hay al menos uno
        RolEntity rol = rolRepo.save(RolEntity.builder()
                .idCompania(ID_COMPANIA).idSucursal(ID_SUCURSAL)
                .nombre("RolListar-" + UUID.randomUUID().toString().substring(0, 6))
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block();
        savedRolId = rol.getId();

        webClient.get().uri("/api/v1/roles")
                .header("Authorization", staffBearer("roles:leer"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .consumeWith(result -> {
                    JsonNode body = result.getResponseBody();
                    assertNotNull(body);
                    assertTrue(body.isArray(), "La respuesta debe ser un arreglo");
                    assertTrue(body.size() >= 1, "Debe haber al menos un rol");
                });
    }

    @Test
    void buscarRolPorId_debeRetornarRolExistente() {
        RolEntity rol = rolRepo.save(RolEntity.builder()
                .idCompania(ID_COMPANIA).idSucursal(ID_SUCURSAL)
                .nombre("RolBuscar-" + UUID.randomUUID().toString().substring(0, 6))
                .descripcion("Rol para buscar por id")
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block();
        savedRolId = rol.getId();

        webClient.get().uri("/api/v1/roles/" + savedRolId)
                .header("Authorization", staffBearer("roles:leer"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .consumeWith(result -> {
                    JsonNode body = result.getResponseBody();
                    assertNotNull(body);
                    assertEquals(savedRolId.intValue(), body.get("id").asInt());
                    assertEquals("Rol para buscar por id", body.get("descripcion").asText());
                });
    }

    @Test
    void verPermisosDeRol_debeRetornarRolConListaPermisos() {
        RolEntity rol = rolRepo.save(RolEntity.builder()
                .idCompania(ID_COMPANIA).idSucursal(ID_SUCURSAL)
                .nombre("RolVerPermisos-" + UUID.randomUUID().toString().substring(0, 6))
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block();
        savedRolId = rol.getId();

        webClient.get().uri("/api/v1/roles/" + savedRolId + "/permisos")
                .header("Authorization", staffBearer("roles:leer"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .consumeWith(result -> {
                    JsonNode body = result.getResponseBody();
                    assertNotNull(body);
                    assertNotNull(body.get("rol"), "La respuesta debe incluir el campo 'rol'");
                    assertEquals(savedRolId.intValue(), body.get("rol").get("id").asInt());
                    assertNotNull(body.get("permisos"), "La respuesta debe incluir el campo 'permisos'");
                    assertTrue(body.get("permisos").isArray(), "'permisos' debe ser un arreglo");
                });
    }

    @Test
    void actualizarPermisosDeRol_debeAsignarPermisos() {
        savedRolId = rolRepo.save(RolEntity.builder()
                .idCompania(ID_COMPANIA).idSucursal(ID_SUCURSAL)
                .nombre("RolActPermisos-" + UUID.randomUUID().toString().substring(0, 6))
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block().getId();

        savedPermisoId = permisoRepo.save(PermisoEntity.builder()
                .idCompania(ID_COMPANIA).idSucursal(ID_SUCURSAL)
                .nombre("perm-rol-" + UUID.randomUUID().toString().substring(0, 6))
                .modulo("test")
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block().getId();

        webClient.put().uri("/api/v1/roles/" + savedRolId + "/permisos")
                .header("Authorization", staffBearer("roles:crear"))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("id_permisos", List.of(savedPermisoId)))
                .exchange()
                .expectStatus().isOk();

        Long count = db.sql("""
                SELECT COUNT(*) AS cnt FROM seguridad.rol_permisos
                WHERE id_rol = :idRol AND id_permiso = :idPermiso
                """)
                .bind("idRol", savedRolId)
                .bind("idPermiso", savedPermisoId)
                .map((row, meta) -> row.get("cnt", Long.class))
                .one().block();
        assertEquals(1L, count, "El permiso debe estar asignado al rol en la BD");
    }
}
