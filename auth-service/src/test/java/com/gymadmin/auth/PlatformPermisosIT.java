package com.gymadmin.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.PermisoEntity;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.repository.PermisoR2dbcRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlatformPermisosIT extends IntegrationTestBase {

    @Autowired
    private DatabaseClient db;

    @Autowired
    private PermisoR2dbcRepository permisoRepo;

    private Integer savedCompaniaId;
    private Integer savedSucursalId;
    private Integer savedPermisoId;
    private final List<Integer> extraPermisoIds = new ArrayList<>();

    @BeforeEach
    void seedTestData() {
        savedCompaniaId = db.sql("""
                        INSERT INTO tenant.companias (nombre, ruc, activo)
                        VALUES (:nombre, :ruc, true)
                        RETURNING id
                        """)
                .bind("nombre", "Compania Permisos IT")
                .bind("ruc", "9999999999003")
                .map((row, meta) -> row.get("id", Integer.class))
                .one().block();

        savedSucursalId = db.sql("""
                        INSERT INTO tenant.sucursales (id_compania, nombre)
                        VALUES (:idCompania, :nombre)
                        RETURNING id
                        """)
                .bind("idCompania", savedCompaniaId)
                .bind("nombre", "Sucursal Permisos IT")
                .map((row, meta) -> row.get("id", Integer.class))
                .one().block();

        PermisoEntity permiso = permisoRepo.save(PermisoEntity.builder()
                .idCompania(savedCompaniaId)
                .idSucursal(savedSucursalId)
                .nombre("socios:crear_plt")
                .modulo("socios")
                .descripcion("Crear socios Permisos IT")
                .creacionFecha(OffsetDateTime.now())
                .creacionUsuario("test")
                .build()).block();
        savedPermisoId = permiso.getId();
    }

    @AfterEach
    void cleanup() {
        for (Integer id : extraPermisoIds) {
            db.sql("DELETE FROM seguridad.rol_permisos WHERE id_permiso = :id")
                    .bind("id", id).fetch().rowsUpdated().block();
            permisoRepo.deleteById(id).block();
        }
        extraPermisoIds.clear();

        if (savedPermisoId != null) {
            db.sql("DELETE FROM seguridad.rol_permisos WHERE id_permiso = :id")
                    .bind("id", savedPermisoId).fetch().rowsUpdated().block();
            permisoRepo.deleteById(savedPermisoId).block();
            savedPermisoId = null;
        }
        if (savedSucursalId != null) {
            db.sql("DELETE FROM tenant.sucursales WHERE id = :id")
                    .bind("id", savedSucursalId).fetch().rowsUpdated().block();
            savedSucursalId = null;
        }
        if (savedCompaniaId != null) {
            db.sql("DELETE FROM tenant.companias WHERE id = :id")
                    .bind("id", savedCompaniaId).fetch().rowsUpdated().block();
            savedCompaniaId = null;
        }
    }

    // ── listar ──────────────────────────────────────────────────────────────────

    @Test
    void listar_debeIncluirPermisoActivoConNombreSucursal() {
        webClient.get().uri("/api/v1/platform/permisos")
                .header("Authorization", platformBearer())
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .consumeWith(result -> {
                    JsonNode body = result.getResponseBody();
                    assertNotNull(body);
                    assertTrue(body.isArray());

                    JsonNode found = null;
                    for (JsonNode node : body) {
                        if (savedPermisoId.equals(node.get("id").asInt())) {
                            found = node;
                            break;
                        }
                    }
                    assertNotNull(found, "El permiso creado debe aparecer en la lista");
                    assertEquals("socios:crear_plt", found.get("nombre").asText());
                    assertEquals("socios", found.get("modulo").asText());
                    assertEquals(savedCompaniaId, found.get("id_compania").asInt());
                    assertEquals(savedSucursalId, found.get("id_sucursal").asInt());
                    assertEquals("Sucursal Permisos IT", found.get("nombre_sucursal").asText());
                });
    }

    @Test
    void listar_tokenNoPlataforma_debeRetornar403() {
        webClient.get().uri("/api/v1/platform/permisos")
                .header("Authorization", staffBearer())
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── crear ───────────────────────────────────────────────────────────────────

    @Test
    void crear_datosValidos_debeRetornar201ConPermiso() {
        Map<String, Object> body = Map.of(
                "nombre", "clases:gestionar_plt",
                "modulo", "clases",
                "descripcion", "Gestionar clases IT",
                "id_compania", savedCompaniaId,
                "id_sucursal", savedSucursalId
        );

        webClient.post().uri("/api/v1/platform/permisos")
                .header("Authorization", platformBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(JsonNode.class)
                .consumeWith(result -> {
                    JsonNode r = result.getResponseBody();
                    assertNotNull(r);
                    Integer newId = r.get("id").asInt();
                    extraPermisoIds.add(newId);

                    assertEquals("clases:gestionar_plt", r.get("nombre").asText());
                    assertEquals("clases", r.get("modulo").asText());
                    assertEquals("Gestionar clases IT", r.get("descripcion").asText());
                    assertEquals(savedCompaniaId, r.get("id_compania").asInt());
                    assertEquals(savedSucursalId, r.get("id_sucursal").asInt());
                    assertEquals("Sucursal Permisos IT", r.get("nombre_sucursal").asText());
                });
    }

    @Test
    void crear_nombreDuplicadoMismaCompania_debeRetornar409() {
        Map<String, Object> body = Map.of(
                "nombre", "socios:crear_plt",
                "modulo", "socios",
                "id_compania", savedCompaniaId,
                "id_sucursal", savedSucursalId
        );

        webClient.post().uri("/api/v1/platform/permisos")
                .header("Authorization", platformBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void crear_camposObligatoriosFaltantes_debeRetornar400() {
        webClient.post().uri("/api/v1/platform/permisos")
                .header("Authorization", platformBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("modulo", "socios"))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void crear_tokenNoSuperAdmin_debeRetornar403() {
        Map<String, Object> body = Map.of(
                "nombre", "cualquier:permiso",
                "modulo", "cualquier",
                "id_compania", savedCompaniaId,
                "id_sucursal", savedSucursalId
        );

        webClient.post().uri("/api/v1/platform/permisos")
                .header("Authorization", staffBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── actualizar ──────────────────────────────────────────────────────────────

    @Test
    void actualizar_datosValidos_debeRetornar200ConPermisoCambiado() {
        webClient.put().uri("/api/v1/platform/permisos/" + savedPermisoId)
                .header("Authorization", platformBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("nombre", "socios:crear_updated", "descripcion", "Descripcion actualizada"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .consumeWith(result -> {
                    JsonNode r = result.getResponseBody();
                    assertNotNull(r);
                    assertEquals(savedPermisoId, r.get("id").asInt());
                    assertEquals("socios:crear_updated", r.get("nombre").asText());
                    assertEquals("Descripcion actualizada", r.get("descripcion").asText());
                    assertEquals("Sucursal Permisos IT", r.get("nombre_sucursal").asText());
                });
    }

    @Test
    void actualizar_idInexistente_debeRetornar404() {
        webClient.put().uri("/api/v1/platform/permisos/999999")
                .header("Authorization", platformBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("nombre", "cualquier:permiso"))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void actualizar_tokenNoSuperAdmin_debeRetornar403() {
        webClient.put().uri("/api/v1/platform/permisos/" + savedPermisoId)
                .header("Authorization", staffBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("nombre", "socios:leer"))
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── eliminar ────────────────────────────────────────────────────────────────

    @Test
    void eliminar_permisoExistente_debeRetornar204YSoftDelete() {
        webClient.delete().uri("/api/v1/platform/permisos/" + savedPermisoId)
                .header("Authorization", platformBearer())
                .exchange()
                .expectStatus().isNoContent();

        // Verify soft-delete in DB
        Boolean eliminado = db.sql("SELECT eliminado FROM seguridad.permisos WHERE id = :id")
                .bind("id", savedPermisoId)
                .map((row, meta) -> row.get("eliminado", Boolean.class))
                .one().block();
        assertTrue(eliminado, "El permiso debe estar marcado como eliminado=true");
    }

    @Test
    void eliminar_idInexistente_debeRetornar404() {
        webClient.delete().uri("/api/v1/platform/permisos/999999")
                .header("Authorization", platformBearer())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void eliminar_tokenNoSuperAdmin_debeRetornar403() {
        webClient.delete().uri("/api/v1/platform/permisos/" + savedPermisoId)
                .header("Authorization", staffBearer())
                .exchange()
                .expectStatus().isForbidden();
    }
}
