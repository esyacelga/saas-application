package com.gymadmin.auth.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.auth.domain.port.out.PlatformRolPort;
import com.gymadmin.auth.dto.response.CompaniaBasicaResponse;
import com.gymadmin.auth.dto.response.RolPlataformaResponse;
import com.gymadmin.auth.dto.response.SucursalBasicaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
public class PlatformRolPersistenceAdapter implements PlatformRolPort {

    private final DatabaseClient db;

    private static final String ROLES_BASE_SQL = """
            SELECT
                r.id,
                r.nombre,
                r.descripcion,
                c.id        AS id_compania,
                c.nombre    AS nombre_compania,
                COUNT(u.id) AS total_usuarios
            FROM seguridad.roles r
            left outer join tenant.companias c ON c.id = r.id_compania
            LEFT JOIN seguridad.usuarios u ON u.id_rol = r.id
            """;

    private static final String ROLES_GROUP_ORDER = """
            GROUP BY r.id, r.nombre, r.descripcion, c.id, c.nombre
            ORDER BY c.nombre ASC, r.nombre ASC
            """;

    @Override
    public Flux<RolPlataformaResponse> findAllRoles() {
        return db.sql(ROLES_BASE_SQL + ROLES_GROUP_ORDER)
                .map((row, meta) -> mapRol(row))
                .all();
    }

    @Override
    public Mono<RolPlataformaResponse> findRolById(Integer id) {
        return db.sql(ROLES_BASE_SQL + "WHERE r.id = :id " + ROLES_GROUP_ORDER)
                .bind("id", id)
                .map((row, meta) -> mapRol(row))
                .one();
    }

    @Override
    public Flux<CompaniaBasicaResponse> findAllCompanias() {
        return db.sql("SELECT id, nombre FROM tenant.companias WHERE activo = true ORDER BY nombre ASC")
                .map((row, meta) -> new CompaniaBasicaResponse(
                        row.get("id", Integer.class),
                        row.get("nombre", String.class)))
                .all();
    }

    @Override
    public Mono<RolPlataformaResponse> save(Integer idCompania, String nombre, String descripcion, String createdBy, Integer idSucursal) {
        OffsetDateTime now = OffsetDateTime.now();
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        INSERT INTO seguridad.roles (id_compania, nombre, descripcion, creacion_usuario, creacion_fecha, id_sucursal    )
                        VALUES (:idCompania, :nombre, :descripcion, :createdBy, :fecha, :idSucursal)
                        RETURNING id
                        """)
                .bind("idCompania", idCompania)
                .bind("idSucursal", idSucursal)
                .bind("nombre", nombre)
                .bind("createdBy", createdBy)
                .bind("fecha", now);
        spec = descripcion != null ? spec.bind("descripcion", descripcion) : spec.bindNull("descripcion", String.class);
        return spec.map((row, meta) -> row.get("id", Integer.class))
                .one()
                .flatMap(this::findRolById);
    }

    @Override
    public Mono<RolPlataformaResponse> update(Integer id, String nombre, String descripcion, String updatedBy) {
        OffsetDateTime now = OffsetDateTime.now();
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        UPDATE seguridad.roles
                        SET nombre = :nombre, descripcion = :descripcion,
                            modifica_usuario = :updatedBy, modifica_fecha = :fecha
                        WHERE id = :id
                        """)
                .bind("id", id)
                .bind("nombre", nombre)
                .bind("updatedBy", updatedBy)
                .bind("fecha", now);
        spec = descripcion != null ? spec.bind("descripcion", descripcion) : spec.bindNull("descripcion", String.class);
        return spec.fetch().rowsUpdated().then(findRolById(id));
    }

    @Override
    public Mono<Long> countUsuariosByRolId(Integer id) {
        return db.sql("SELECT COUNT(*) AS cnt FROM seguridad.usuarios WHERE id_rol = :id AND eliminado = false")
                .bind("id", id)
                .map((row, meta) -> row.get("cnt", Long.class))
                .one();
    }

    @Override
    public Mono<Boolean> existsCompania(Integer idCompania) {
        return db.sql("SELECT COUNT(*) AS cnt FROM tenant.companias WHERE id = :id")
                .bind("id", idCompania)
                .map((row, meta) -> row.get("cnt", Long.class))
                .one()
                .map(count -> count > 0);
    }

    @Override
    public Flux<SucursalBasicaResponse> findSucursalesByCompania(Integer idCompania) {
        return db.sql("SELECT id, nombre FROM tenant.sucursales WHERE id_compania = :idCompania ORDER BY nombre ASC")
                .bind("idCompania", idCompania)
                .map((row, meta) -> new SucursalBasicaResponse(
                        row.get("id", Integer.class),
                        row.get("nombre", String.class)))
                .all();
    }

    @Override
    public Mono<Void> deleteById(Integer id) {
        return db.sql("DELETE FROM seguridad.roles WHERE id = :id")
                .bind("id", id)
                .fetch().rowsUpdated()
                .then();
    }

    private RolPlataformaResponse mapRol(io.r2dbc.spi.Row row) {
        return new RolPlataformaResponse(
                row.get("id", Integer.class),
                row.get("nombre", String.class),
                row.get("descripcion", String.class),
                row.get("id_compania", Integer.class),
                row.get("nombre_compania", String.class),
                row.get("total_usuarios", Long.class));
    }
}
