package com.gymadmin.auth.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.auth.domain.exception.ConflictException;
import com.gymadmin.auth.domain.exception.ResourceNotFoundException;
import com.gymadmin.auth.domain.port.out.PlatformPermisoPort;
import com.gymadmin.auth.dto.request.CreatePermisoRequest;
import com.gymadmin.auth.dto.request.UpdatePermisoRequest;
import com.gymadmin.auth.dto.response.PermisoPlataformaResponse;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.PermisoEntity;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.repository.PermisoR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
public class PlatformPermisoPersistenceAdapter implements PlatformPermisoPort {

    private final DatabaseClient db;
    private final PermisoR2dbcRepository repo;

    private static final String PERMISOS_WITH_SUCURSAL_SQL = """
            SELECT p.id, p.nombre, p.modulo, p.descripcion,
                   p.id_compania, p.id_sucursal,
                   s.nombre AS nombre_sucursal
            FROM seguridad.permisos p
            INNER JOIN tenant.sucursales s ON s.id = p.id_sucursal
            WHERE p.eliminado = false
            ORDER BY p.modulo ASC, p.nombre ASC
            """;

    @Override
    public Flux<PermisoPlataformaResponse> findAllWithSucursal() {
        return db.sql(PERMISOS_WITH_SUCURSAL_SQL)
                .map((row, meta) -> mapRow(row))
                .all();
    }

    @Override
    public Mono<PermisoPlataformaResponse> findByIdWithSucursal(Integer id) {
        return db.sql("""
                        SELECT p.id, p.nombre, p.modulo, p.descripcion,
                               p.id_compania, p.id_sucursal,
                               s.nombre AS nombre_sucursal
                        FROM seguridad.permisos p
                        INNER JOIN tenant.sucursales s ON s.id = p.id_sucursal
                        WHERE p.id = :id AND p.eliminado = false
                        """)
                .bind("id", id)
                .map((row, meta) -> mapRow(row))
                .one();
    }

    @Override
    public Mono<Boolean> existsByIdCompaniaAndNombreAndNotDeleted(Integer idCompania, String nombre) {
        return repo.existsByIdCompaniaAndNombreAndEliminadoFalse(idCompania, nombre);
    }

    @Override
    public Mono<PermisoPlataformaResponse> create(CreatePermisoRequest req, String createdBy) {
        OffsetDateTime now = OffsetDateTime.now();
        // If a soft-deleted record with the same (id_compania, nombre) exists, reactivate it
        // instead of inserting — this avoids unique-constraint violations.
        return db.sql("""
                        SELECT id FROM seguridad.permisos
                        WHERE id_compania = :idCompania AND nombre = :nombre AND eliminado = true
                        LIMIT 1
                        """)
                .bind("idCompania", req.idCompania())
                .bind("nombre", req.nombre())
                .map((row, meta) -> row.get("id", Integer.class))
                .one()
                .flatMap(existingId -> {
                    DatabaseClient.GenericExecuteSpec upd = db.sql("""
                                    UPDATE seguridad.permisos
                                    SET eliminado = false,
                                        modulo = :modulo,
                                        descripcion = :descripcion,
                                        modifica_fecha = :fecha,
                                        modifica_usuario = :usuario
                                    WHERE id = :id
                                    """)
                            .bind("id", existingId)
                            .bind("modulo", req.modulo())
                            .bind("fecha", now)
                            .bind("usuario", createdBy);
                    upd = req.descripcion() != null
                            ? upd.bind("descripcion", req.descripcion())
                            : upd.bindNull("descripcion", String.class);
                    return upd.fetch().rowsUpdated().thenReturn(existingId);
                })
                .switchIfEmpty(insertNew(req, now, createdBy))
                .flatMap(id -> findByIdWithSucursal(id)
                        .switchIfEmpty(Mono.error(new ResourceNotFoundException("Permiso recién creado no encontrado: " + id))));
    }

    private Mono<Integer> insertNew(CreatePermisoRequest req, OffsetDateTime now, String createdBy) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        INSERT INTO seguridad.permisos
                            (nombre, modulo, descripcion, id_compania, id_sucursal, eliminado, creacion_fecha, creacion_usuario)
                        VALUES (:nombre, :modulo, :descripcion, :idCompania, :idSucursal, false, :fecha, :usuario)
                        RETURNING id
                        """)
                .bind("nombre", req.nombre())
                .bind("modulo", req.modulo())
                .bind("idCompania", req.idCompania())
                .bind("idSucursal", req.idSucursal())
                .bind("fecha", now)
                .bind("usuario", createdBy);
        spec = req.descripcion() != null
                ? spec.bind("descripcion", req.descripcion())
                : spec.bindNull("descripcion", String.class);
        return spec.map((row, meta) -> row.get("id", Integer.class)).one();
    }

    @Override
    public Mono<PermisoPlataformaResponse> update(Integer id, UpdatePermisoRequest req, String updatedBy) {
        OffsetDateTime now = OffsetDateTime.now();
        return repo.findByIdAndEliminadoFalse(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Permiso no encontrado: " + id)))
                .flatMap(entity -> {
                    if (req.nombre() != null) entity.setNombre(req.nombre());
                    if (req.modulo() != null) entity.setModulo(req.modulo());
                    if (req.descripcion() != null) entity.setDescripcion(req.descripcion());
                    entity.setModificaFecha(now);
                    entity.setModificaUsuario(updatedBy);
                    return repo.save(entity);
                })
                .flatMap(saved -> findByIdWithSucursal(saved.getId())
                        .switchIfEmpty(Mono.error(new ResourceNotFoundException("Permiso no encontrado tras actualización: " + id))));
    }

    @Override
    public Mono<Void> softDelete(Integer id, String updatedBy) {
        OffsetDateTime now = OffsetDateTime.now();
        return db.sql("""
                        UPDATE seguridad.permisos
                        SET eliminado = true, modifica_fecha = :fecha, modifica_usuario = :usuario
                        WHERE id = :id AND eliminado = false
                        """)
                .bind("id", id)
                .bind("fecha", now)
                .bind("usuario", updatedBy)
                .fetch().rowsUpdated()
                .flatMap(rows -> rows == 0
                        ? Mono.error(new ResourceNotFoundException("Permiso no encontrado o ya eliminado: " + id))
                        : Mono.<Void>empty());
    }

    private PermisoPlataformaResponse mapRow(io.r2dbc.spi.Row row) {
        return new PermisoPlataformaResponse(
                row.get("id", Integer.class),
                row.get("nombre", String.class),
                row.get("modulo", String.class),
                row.get("descripcion", String.class),
                row.get("id_compania", Integer.class),
                row.get("id_sucursal", Integer.class),
                row.get("nombre_sucursal", String.class)
        );
    }
}
