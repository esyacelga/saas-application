package com.gymadmin.auth.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.auth.domain.exception.ConflictException;
import com.gymadmin.auth.domain.exception.ResourceNotFoundException;
import com.gymadmin.auth.domain.model.Permiso;
import com.gymadmin.auth.domain.port.out.RolPermisoPort;
import com.gymadmin.auth.dto.response.PermisoRolResponse;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.mapper.PermisoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
public class RolPermisoPersistenceAdapter implements RolPermisoPort {

    private final DatabaseClient db;

    @Override
    public Flux<String> findNombresPermisoByIdRol(Integer idRol) {
        return db.sql("""
                        SELECT p.nombre FROM seguridad.permisos p
                        JOIN seguridad.rol_permisos rp ON p.id = rp.id_permiso
                        WHERE rp.id_rol = :idRol
                        """)
                .bind("idRol", idRol)
                .map((row, meta) -> row.get("nombre", String.class))
                .all();
    }

    @Override
    public Flux<Permiso> findPermisosWithDetailByIdRol(Integer idRol) {
        return db.sql("""
                        SELECT p.* FROM seguridad.permisos p
                        JOIN seguridad.rol_permisos rp ON p.id = rp.id_permiso
                        WHERE rp.id_rol = :idRol
                        """)
                .bind("idRol", idRol)
                .map((row, meta) -> PermisoMapper.fromRow(row))
                .all();
    }

    @Override
    public Mono<Void> deleteByIdRol(Integer idRol) {
        return db.sql("DELETE FROM seguridad.rol_permisos WHERE id_rol = :idRol")
                .bind("idRol", idRol)
                .fetch().rowsUpdated()
                .then();
    }

    @Override
    public Mono<Void> saveAll(Integer idRol, Iterable<Integer> idPermisos, String createdBy) {
        OffsetDateTime now = OffsetDateTime.now();
        return Flux.fromIterable(idPermisos)
                .concatMap(idPermiso -> db.sql("""
                                INSERT INTO seguridad.rol_permisos
                                (id_rol, id_permiso, creacion_fecha, creacion_usuario)
                                VALUES (:idRol, :idPermiso, :fecha, :usuario)
                                """)
                        .bind("idRol", idRol)
                        .bind("idPermiso", idPermiso)
                        .bind("fecha", now)
                        .bind("usuario", createdBy)
                        .fetch().rowsUpdated())
                .then();
    }

    @Override
    public Flux<PermisoRolResponse> findPermisosConSucursalByIdRol(Integer idRol) {
        return db.sql("""
                        SELECT p.id,
                               s.nombre AS nombre_sucursal,
                               p.nombre,
                               p.descripcion,
                               p.modulo
                        FROM seguridad.permisos p
                        INNER JOIN tenant.sucursales s ON s.id = p.id_sucursal
                        INNER JOIN seguridad.rol_permisos rp ON rp.id_permiso = p.id
                        WHERE rp.id_rol = :idRol
                          AND rp.eliminado = false
                          AND p.eliminado = false
                        ORDER BY p.modulo, p.nombre
                        """)
                .bind("idRol", idRol)
                .map((row, meta) -> new PermisoRolResponse(
                        row.get("id", Integer.class),
                        row.get("nombre_sucursal", String.class),
                        row.get("nombre", String.class),
                        row.get("descripcion", String.class),
                        row.get("modulo", String.class)
                ))
                .all();
    }

    @Override
    public Mono<Void> asignar(Integer idRol, Integer idPermiso, String createdBy) {
        OffsetDateTime now = OffsetDateTime.now();
        return db.sql("""
                        SELECT eliminado FROM seguridad.rol_permisos
                        WHERE id_rol = :idRol AND id_permiso = :idPermiso
                        """)
                .bind("idRol", idRol)
                .bind("idPermiso", idPermiso)
                .map((row, meta) -> row.get("eliminado", Boolean.class))
                .one()
                .flatMap(eliminado -> {
                    if (Boolean.FALSE.equals(eliminado)) {
                        return Mono.<Boolean>error(new ConflictException("El permiso ya está asignado a este rol"));
                    }
                    // Reactivate soft-deleted record; thenReturn keeps Mono non-empty so switchIfEmpty won't fire
                    return db.sql("""
                                    UPDATE seguridad.rol_permisos
                                    SET eliminado = false,
                                        modifica_fecha = :fecha,
                                        modifica_usuario = :usuario
                                    WHERE id_rol = :idRol AND id_permiso = :idPermiso
                                    """)
                            .bind("idRol", idRol)
                            .bind("idPermiso", idPermiso)
                            .bind("fecha", now)
                            .bind("usuario", createdBy)
                            .fetch().rowsUpdated().thenReturn(true);
                })
                .switchIfEmpty(db.sql("""
                                INSERT INTO seguridad.rol_permisos
                                (id_rol, id_permiso, eliminado, creacion_fecha, creacion_usuario)
                                VALUES (:idRol, :idPermiso, false, :fecha, :usuario)
                                """)
                        .bind("idRol", idRol)
                        .bind("idPermiso", idPermiso)
                        .bind("fecha", now)
                        .bind("usuario", createdBy)
                        .fetch().rowsUpdated().thenReturn(false))
                .then();
    }

    @Override
    public Mono<Void> softDeleteAsignacion(Integer idRol, Integer idPermiso, String updatedBy) {
        OffsetDateTime now = OffsetDateTime.now();
        return db.sql("""
                        UPDATE seguridad.rol_permisos
                        SET eliminado = true,
                            modifica_fecha = :fecha,
                            modifica_usuario = :usuario
                        WHERE id_rol = :idRol
                          AND id_permiso = :idPermiso
                          AND eliminado = false
                        """)
                .bind("idRol", idRol)
                .bind("idPermiso", idPermiso)
                .bind("fecha", now)
                .bind("usuario", updatedBy)
                .fetch().rowsUpdated()
                .flatMap(rows -> rows == 0
                        ? Mono.error(new ResourceNotFoundException(
                                "Asignación no encontrada o ya eliminada (idRol=" + idRol + ", idPermiso=" + idPermiso + ")"))
                        : Mono.<Void>empty());
    }
}
