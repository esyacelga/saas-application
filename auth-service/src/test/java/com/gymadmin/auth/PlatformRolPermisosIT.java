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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlatformRolPermisosIT extends IntegrationTestBase {

    @Autowired
    private DatabaseClient db;

    @Autowired
    private PermisoR2dbcRepository permisoRepo;

    private Integer savedCompaniaId;
    private Integer savedSucursalId;
    private Integer savedRolId;
    private Integer savedPermisoId;

    @BeforeEach
    void seedTestData() {
        savedCompaniaId = db.sql("""
                        INSERT INTO tenant.companias (nombre, ruc, activo)
                        VALUES (:nombre, :ruc, true)
                        RETURNING id
                        """)
                .bind("nombre", "Compania RolPermisos IT")
                .bind("ruc", "9999999999002")
                .map((row, meta) -> row.get("id", Integer.class))
                .one().block();

        savedSucursalId = db.sql("""
                        INSERT INTO tenant.sucursales (id_compania, nombre)
                        VALUES (:idCompania, :nombre)
                        RETURNING id
                        """)
                .bind("idCompania", savedCompaniaId)
                .bind("nombre", "Sucursal RolPermisos IT")
                .map((row, meta) -> row.get("id", Integer.class))
                .one().block();

        RolEntity rol = rolRepo.save(RolEntity.builder()
                .idCompania(savedCompaniaId)
                .idSucursal(savedSucursalId)
                .nombre("Rol RolPermisos IT")
                .descripcion("Rol para test granular")
                .creacionFecha(OffsetDateTime.now())
                .creacionUsuario("test")
                .build()).block();
        savedRolId = rol.getId();

        PermisoEntity permiso = permisoRepo.save(PermisoEntity.builder()
                .idCompania(savedCompaniaId)
                .idSucursal(savedSucursalId)
                .nombre("socios:leer_rp")
                .modulo("socios")
                .descripcion("Ver socios RolPermisos IT")
                .creacionFecha(OffsetDateTime.now())
                .creacionUsuario("test")
                .build()).block();
        savedPermisoId = permiso.getId();

        // Seed an active rol_permiso assignment
        db.sql("""
                        INSERT INTO seguridad.rol_permisos
                        (id_rol, id_permiso, eliminado, creacion_fecha, creacion_usuario)
                        VALUES (:idRol, :idPermiso, false, :fecha, :usuario)
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
            db.sql("DELETE FROM seguridad.rol_permisos WHERE id_rol = :id")
                    .bind("id", savedRolId)
                    .fetch().rowsUpdated().block();
            rolRepo.deleteById(savedRolId).block();
            savedRolId = null;
        }
        if (savedPermisoId != null) {
            permisoRepo.deleteById(savedPermisoId).block();
            savedPermisoId = null;
        }
        if (savedSucursalId != null) {
            db.sql("DELETE FROM tenant.sucursales WHERE id = :id")
                    .bind("id", savedSucursalId)
                    .fetch().rowsUpdated().block();
            savedSucursalId = null;
        }
        if (savedCompaniaId != null) {
            db.sql("DELETE FROM tenant.companias WHERE id = :id")
                    .bind("id", savedCompaniaId)
                    .fetch().rowsUpdated().block();
            savedCompaniaId = null;
        }
    }

    // ── verPermisosDetalle ──────────────────────────────────────────────────────

    @Test
    void verPermisosDetalle_rolConPermisoActivo_debeRetornarListaConDatosDeSucursal() {
        webClient.get().uri("/api/v1/platform/roles/" + savedRolId + "/permisos/detalle")
                .header("Authorization", platformBearer())
                .exchange()
                .expectStatus().isOk()
                .expectBody(JsonNode.class)
                .consumeWith(result -> {
                    JsonNode body = result.getResponseBody();
                    assertNotNull(body);
                    assertTrue(body.isArray());
                    assertEquals(1, body.size(), "Debe haber exactamente un permiso activo");

                    JsonNode p = body.get(0);
                    assertEquals(savedPermisoId, p.get("id").asInt());
                    assertEquals("socios:leer_rp", p.get("nombre").asText());
                    assertEquals("socios", p.get("modulo").asText());
                    assertEquals("Sucursal RolPermisos IT", p.get("nombre_sucursal").asText());
                });
    }

    @Test
    void verPermisosDetalle_rolInexistente_debeRetornar404() {
        webClient.get().uri("/api/v1/platform/roles/999999/permisos/detalle")
                .header("Authorization", platformBearer())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void verPermisosDetalle_tokenNoPlataforma_debeRetornar403() {
        webClient.get().uri("/api/v1/platform/roles/" + savedRolId + "/permisos/detalle")
                .header("Authorization", staffBearer())
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── asignarPermiso ──────────────────────────────────────────────────────────

    @Test
    void asignarPermiso_permisoNuevo_debeRetornar201() {
        // Create a second permiso not yet assigned
        Integer segundoPermisoId = permisoRepo.save(PermisoEntity.builder()
                .idCompania(savedCompaniaId)
                .idSucursal(savedSucursalId)
                .nombre("clases:leer_rp")
                .modulo("clases")
                .descripcion("Ver clases RolPermisos IT")
                .creacionFecha(OffsetDateTime.now())
                .creacionUsuario("test")
                .build()).block().getId();

        try {
            webClient.post().uri("/api/v1/platform/roles/" + savedRolId + "/permisos")
                    .header("Authorization", platformBearer())
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(Map.of("id_permiso", segundoPermisoId))
                    .exchange()
                    .expectStatus().isCreated();

            // Verify assignment is in DB
            Long count = db.sql("""
                            SELECT COUNT(*) AS cnt FROM seguridad.rol_permisos
                            WHERE id_rol = :idRol AND id_permiso = :idPermiso AND eliminado = false
                            """)
                    .bind("idRol", savedRolId)
                    .bind("idPermiso", segundoPermisoId)
                    .map((row, meta) -> row.get("cnt", Long.class))
                    .one().block();
            assertEquals(1L, count, "La asignación debe existir y no estar eliminada");
        } finally {
            db.sql("DELETE FROM seguridad.rol_permisos WHERE id_permiso = :id")
                    .bind("id", segundoPermisoId)
                    .fetch().rowsUpdated().block();
            permisoRepo.deleteById(segundoPermisoId).block();
        }
    }

    @Test
    void asignarPermiso_yaAsignado_debeRetornar409() {
        webClient.post().uri("/api/v1/platform/roles/" + savedRolId + "/permisos")
                .header("Authorization", platformBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("id_permiso", savedPermisoId))
                .exchange()
                .expectStatus().isEqualTo(409);
    }

    @Test
    void asignarPermiso_softDeletedPrevio_debeReactivarYRetornar201() {
        // Soft-delete the existing assignment first
        db.sql("""
                        UPDATE seguridad.rol_permisos
                        SET eliminado = true
                        WHERE id_rol = :idRol AND id_permiso = :idPermiso
                        """)
                .bind("idRol", savedRolId)
                .bind("idPermiso", savedPermisoId)
                .fetch().rowsUpdated().block();

        webClient.post().uri("/api/v1/platform/roles/" + savedRolId + "/permisos")
                .header("Authorization", platformBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("id_permiso", savedPermisoId))
                .exchange()
                .expectStatus().isCreated();

        // Verify reactivation
        Boolean eliminado = db.sql("""
                        SELECT eliminado FROM seguridad.rol_permisos
                        WHERE id_rol = :idRol AND id_permiso = :idPermiso
                        """)
                .bind("idRol", savedRolId)
                .bind("idPermiso", savedPermisoId)
                .map((row, meta) -> row.get("eliminado", Boolean.class))
                .one().block();
        assertFalse(eliminado, "El registro debe estar activo (eliminado=false) tras reactivar");
    }

    @Test
    void asignarPermiso_rolInexistente_debeRetornar404() {
        webClient.post().uri("/api/v1/platform/roles/999999/permisos")
                .header("Authorization", platformBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("id_permiso", savedPermisoId))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void asignarPermiso_tokenNoSuperAdmin_debeRetornar403() {
        webClient.post().uri("/api/v1/platform/roles/" + savedRolId + "/permisos")
                .header("Authorization", staffBearer())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("id_permiso", savedPermisoId))
                .exchange()
                .expectStatus().isForbidden();
    }

    // ── eliminarPermisoDeRol ────────────────────────────────────────────────────

    @Test
    void eliminarPermisoDeRol_asignacionActiva_debeRetornar204() {
        webClient.delete()
                .uri("/api/v1/platform/roles/" + savedRolId + "/permisos/" + savedPermisoId)
                .header("Authorization", platformBearer())
                .exchange()
                .expectStatus().isNoContent();

        // Verify soft-delete in DB
        Boolean eliminado = db.sql("""
                        SELECT eliminado FROM seguridad.rol_permisos
                        WHERE id_rol = :idRol AND id_permiso = :idPermiso
                        """)
                .bind("idRol", savedRolId)
                .bind("idPermiso", savedPermisoId)
                .map((row, meta) -> row.get("eliminado", Boolean.class))
                .one().block();
        assertTrue(eliminado, "El registro debe ser soft-deleted (eliminado=true)");
    }

    @Test
    void eliminarPermisoDeRol_asignacionInexistente_debeRetornar404() {
        webClient.delete()
                .uri("/api/v1/platform/roles/" + savedRolId + "/permisos/999999")
                .header("Authorization", platformBearer())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void eliminarPermisoDeRol_yaEliminado_debeRetornar404() {
        // Soft-delete first
        db.sql("UPDATE seguridad.rol_permisos SET eliminado = true WHERE id_rol = :idRol AND id_permiso = :idPermiso")
                .bind("idRol", savedRolId)
                .bind("idPermiso", savedPermisoId)
                .fetch().rowsUpdated().block();

        webClient.delete()
                .uri("/api/v1/platform/roles/" + savedRolId + "/permisos/" + savedPermisoId)
                .header("Authorization", platformBearer())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void eliminarPermisoDeRol_rolInexistente_debeRetornar404() {
        webClient.delete()
                .uri("/api/v1/platform/roles/999999/permisos/" + savedPermisoId)
                .header("Authorization", platformBearer())
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void eliminarPermisoDeRol_tokenNoSuperAdmin_debeRetornar403() {
        webClient.delete()
                .uri("/api/v1/platform/roles/" + savedRolId + "/permisos/" + savedPermisoId)
                .header("Authorization", staffBearer())
                .exchange()
                .expectStatus().isForbidden();
    }
}
