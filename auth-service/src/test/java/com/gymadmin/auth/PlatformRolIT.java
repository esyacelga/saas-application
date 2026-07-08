package com.gymadmin.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.PermisoEntity;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.RolEntity;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.repository.PermisoR2dbcRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlatformRolIT extends IntegrationTestBase {

    @Autowired
    private DatabaseClient db;

    @Autowired
    private PermisoR2dbcRepository permisoRepo;

    private Integer savedCompaniaId;
    private Integer savedRolId;
    private Integer savedPermisoId;

    @BeforeEach
    void seedTestData() {
        savedCompaniaId = db.sql("""
                        INSERT INTO tenant.companias (nombre, ruc, activo)
                        VALUES (:nombre, :ruc, true)
                        RETURNING id
                        """)
                .bind("nombre", "Compania Test IT")
                .bind("ruc", "9999999999001")
                .map((row, meta) -> row.get("id", Integer.class))
                .one().block();

        RolEntity rol = rolRepo.save(RolEntity.builder()
                .idCompania(savedCompaniaId)
                .idSucursal(ID_SUCURSAL)
                .nombre("Rol Test Platform")
                .descripcion("Rol para test de integracion")
                .creacionFecha(OffsetDateTime.now())
                .creacionUsuario("test")
                .build()).block();
        savedRolId = rol.getId();

        PermisoEntity permiso = permisoRepo.save(PermisoEntity.builder()
                .idCompania(savedCompaniaId)
                .idSucursal(ID_SUCURSAL)
                .nombre("socios:leer")
                .modulo("socios")
                .descripcion("Ver socios")
                .creacionFecha(OffsetDateTime.now())
                .creacionUsuario("test")
                .build()).block();
        savedPermisoId = permiso.getId();

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
        }
        if (savedPermisoId != null) {
            permisoRepo.deleteById(savedPermisoId).block();
        }
        if (savedRolId != null) {
            rolRepo.deleteById(savedRolId).block();
            savedRolId = null;
        }
        if (savedCompaniaId != null) {
            db.sql("DELETE FROM tenant.companias WHERE id = :id")
                    .bind("id", savedCompaniaId)
                    .fetch().rowsUpdated().block();
            savedCompaniaId = null;
        }
    }

    @Test
    void listarRoles_sinFiltro_debeIncluirRolCreadoConDatosDeCompania() {
        webClient.get().uri("/api/v1/platform/roles")
                .header("Authorization", platformBearer())
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .consumeWith(result -> {
                    JsonNode body = result.getResponseBody();
                    assertNotNull(body);
                    assertTrue(body.isArray(), "La respuesta debe ser un arreglo");

                    JsonNode rolNode = null;
                    for (JsonNode node : body) {
                        if (savedRolId.equals(node.get("id").asInt())) {
                            rolNode = node;
                            break;
                        }
                    }
                    assertNotNull(rolNode, "El rol creado debe aparecer en la lista");
                    assertEquals("Rol Test Platform", rolNode.get("nombre").asText());
                    assertEquals(savedCompaniaId, rolNode.get("id_compania").asInt());
                    assertEquals("Compania Test IT", rolNode.get("nombre_compania").asText());
                    assertNotNull(rolNode.get("total_usuarios"), "Debe incluir total_usuarios");
                });
    }


    @Test
    void verPermisosPorRol_debeRetornarRolConPermisosAsociados() {
        webClient.get().uri("/api/v1/platform/roles/" + savedRolId + "/permisos")
                .header("Authorization", platformBearer())
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .consumeWith(result -> {
                    JsonNode body = result.getResponseBody();
                    assertNotNull(body);

                    JsonNode rolNode = body.get("rol");
                    assertNotNull(rolNode, "La respuesta debe tener campo 'rol'");
                    assertEquals(savedRolId, rolNode.get("id").asInt());
                    assertEquals("Rol Test Platform", rolNode.get("nombre").asText());
                    assertEquals(savedCompaniaId, rolNode.get("id_compania").asInt());

                    JsonNode permisosNode = body.get("permisos");
                    assertNotNull(permisosNode, "La respuesta debe tener campo 'permisos'");
                    assertTrue(permisosNode.isArray());
                    assertEquals(1, permisosNode.size(), "Debe haber exactamente un permiso asignado");
                    assertEquals(savedPermisoId, permisosNode.get(0).get("id").asInt());
                    assertEquals("socios:leer", permisosNode.get(0).get("nombre").asText());
                    assertEquals("socios", permisosNode.get(0).get("modulo").asText());
                });
    }

    @Test
    void actualizarRol_datosValidos_debeRetornar200ConRolActualizado() {
        webClient.put().uri("/api/v1/platform/roles/" + savedRolId)
                .header("Authorization", platformBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("nombre", "Rol Actualizado IT", "descripcion", "Descripcion actualizada"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .consumeWith(result -> {
                    JsonNode r = result.getResponseBody();
                    assertNotNull(r);
                    assertEquals(savedRolId, r.get("id").asInt());
                    assertEquals("Rol Actualizado IT", r.get("nombre").asText());
                    assertEquals("Descripcion actualizada", r.get("descripcion").asText());
                    assertEquals(savedCompaniaId, r.get("id_compania").asInt());
                    assertNotNull(r.get("total_usuarios"));
                });
    }

    @Test
    void actualizarRol_sinDescripcion_debeRetornar200ConDescripcionNula() {
        webClient.put().uri("/api/v1/platform/roles/" + savedRolId)
                .header("Authorization", platformBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("nombre", "Rol Sin Descripcion"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .consumeWith(result -> {
                    JsonNode r = result.getResponseBody();
                    assertNotNull(r);
                    assertEquals("Rol Sin Descripcion", r.get("nombre").asText());
                    assertTrue(r.get("descripcion").isNull());
                });
    }

    @Test
    void actualizarRol_idInexistente_debeRetornar404() {
        webClient.put().uri("/api/v1/platform/roles/999999")
                .header("Authorization", platformBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("nombre", "Cualquier Nombre"))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void actualizarRol_nombreVacio_debeRetornar400() {
        webClient.put().uri("/api/v1/platform/roles/" + savedRolId)
                .header("Authorization", platformBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("nombre", ""))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void actualizarRol_tokenNoPlataforma_debeRetornar403() {
        webClient.put().uri("/api/v1/platform/roles/" + savedRolId)
                .header("Authorization", staffBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("nombre", "Nombre Valido"))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void listarSucursales_debeRetornarSucursalesDeCompania() {
        Integer sucursalId = db.sql("""
                        INSERT INTO tenant.sucursales (id_compania, nombre)
                        VALUES (:idCompania, :nombre)
                        RETURNING id
                        """)
                .bind("idCompania", savedCompaniaId)
                .bind("nombre", "Sucursal Test IT")
                .map((row, meta) -> row.get("id", Integer.class))
                .one().block();

        try {
            webClient.get().uri("/api/v1/platform/companias/" + savedCompaniaId + "/sucursales")
                    .header("Authorization", platformBearer())
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(JsonNode.class)
                    .consumeWith(result -> {
                        JsonNode body = result.getResponseBody();
                        assertNotNull(body);
                        assertTrue(body.isArray(), "La respuesta debe ser un arreglo");

                        JsonNode sucursalNode = null;
                        for (JsonNode node : body) {
                            if (sucursalId.equals(node.get("id").asInt())) {
                                sucursalNode = node;
                                break;
                            }
                        }
                        assertNotNull(sucursalNode, "La sucursal creada debe aparecer en la lista");
                        assertEquals("Sucursal Test IT", sucursalNode.get("nombre").asText());
                    });
        } finally {
            if (sucursalId != null) {
                db.sql("DELETE FROM tenant.sucursales WHERE id = :id")
                        .bind("id", sucursalId)
                        .fetch().rowsUpdated().block();
            }
        }
    }

    @Test
    void listarSucursales_companiaExisteSinSucursales_debeRetornarListaVacia() {
        webClient.get().uri("/api/v1/platform/companias/" + savedCompaniaId + "/sucursales")
                .header("Authorization", platformBearer())
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .consumeWith(result -> {
                    JsonNode body = result.getResponseBody();
                    assertNotNull(body);
                    assertTrue(body.isArray(), "La respuesta debe ser un arreglo");
                    assertEquals(0, body.size(), "No debe haber sucursales");
                });
    }

    @Test
    void listarSucursales_companiaInexistente_debeRetornar404() {
        webClient.get().uri("/api/v1/platform/companias/999999/sucursales")
                .header("Authorization", platformBearer())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void listarSucursales_tokenNoPlataforma_debeRetornar403() {
        webClient.get().uri("/api/v1/platform/companias/" + savedCompaniaId + "/sucursales")
                .header("Authorization", staffBearer())
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void listarCompanias_debeIncluirCompaniaActivaCreada() {
        webClient.get().uri("/api/v1/platform/companias")
                .header("Authorization", platformBearer())
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .consumeWith(result -> {
                    JsonNode body = result.getResponseBody();
                    assertNotNull(body);
                    assertTrue(body.isArray(), "La respuesta debe ser un arreglo");

                    JsonNode companiaNode = null;
                    for (JsonNode node : body) {
                        if (savedCompaniaId.equals(node.get("id").asInt())) {
                            companiaNode = node;
                            break;
                        }
                    }
                    assertNotNull(companiaNode, "La compania activa creada debe aparecer en la lista");
                    assertEquals("Compania Test IT", companiaNode.get("nombre").asText());
                });
    }

    @Test
    void crearRol_conCompaniaValida_debeRetornar201ConRol() {
        String nombre = "PlatRolCrear-" + UUID.randomUUID().toString().substring(0, 6);

        JsonNode response = webClient.post().uri("/api/v1/platform/roles")
                .header("Authorization", platformBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "nombre", nombre,
                        "id_compania", savedCompaniaId,
                        "id_sucursal", ID_SUCURSAL
                ))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(JsonNode.class)
                .returnResult().getResponseBody();

        assertNotNull(response);
        Integer newRolId = response.get("id").asInt();
        assertTrue(newRolId > 0, "La respuesta debe contener un ID válido");
        assertEquals(nombre, response.get("nombre").asText());
        assertEquals(savedCompaniaId, response.get("id_compania").asInt());

        // Cleanup the created rol
        db.sql("DELETE FROM seguridad.rol_permisos WHERE id_rol = :id")
                .bind("id", newRolId).fetch().rowsUpdated().block();
        rolRepo.deleteById(newRolId).block();
    }

    @Test
    void crearRol_nombreVacio_debeRetornar400() {
        webClient.post().uri("/api/v1/platform/roles")
                .header("Authorization", platformBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "nombre", "",
                        "id_compania", savedCompaniaId
                ))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void crearRol_tokenNoSuperAdmin_debeRetornar403() {
        webClient.post().uri("/api/v1/platform/roles")
                .header("Authorization", staffBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "nombre", "Cualquier Nombre",
                        "id_compania", savedCompaniaId
                ))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void eliminarRol_sinUsuariosAsignados_debeRetornar204() {
        Integer rolToDeleteId = rolRepo.save(RolEntity.builder()
                .idCompania(savedCompaniaId).idSucursal(ID_SUCURSAL)
                .nombre("PlatRolElim-" + UUID.randomUUID().toString().substring(0, 6))
                .creacionFecha(OffsetDateTime.now()).creacionUsuario("test")
                .build()).block().getId();

        webClient.delete().uri("/api/v1/platform/roles/" + rolToDeleteId)
                .header("Authorization", platformBearer())
                .exchange()
                .expectStatus().isNoContent();

        RolEntity inDb = rolRepo.findByIdAndIdCompania(rolToDeleteId, savedCompaniaId).block();
        assertNull(inDb, "El rol debe haber sido eliminado de la BD");
    }

    @Test
    void eliminarRol_idInexistente_debeRetornar404() {
        webClient.delete().uri("/api/v1/platform/roles/999999")
                .header("Authorization", platformBearer())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void eliminarRol_tokenNoSuperAdmin_debeRetornar403() {
        webClient.delete().uri("/api/v1/platform/roles/" + savedRolId)
                .header("Authorization", staffBearer())
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void actualizarPermisosDeRol_debeReemplazarPermisos() {
        webClient.put().uri("/api/v1/platform/roles/" + savedRolId + "/permisos")
                .header("Authorization", platformBearer())
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
        assertEquals(1L, count, "El permiso debe estar asignado al rol");
    }

    @Test
    void actualizarPermisosDeRol_tokenNoSuperAdmin_debeRetornar403() {
        webClient.put().uri("/api/v1/platform/roles/" + savedRolId + "/permisos")
                .header("Authorization", staffBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("id_permisos", List.of(savedPermisoId)))
                .exchange()
                .expectStatus().isForbidden();
    }
}
