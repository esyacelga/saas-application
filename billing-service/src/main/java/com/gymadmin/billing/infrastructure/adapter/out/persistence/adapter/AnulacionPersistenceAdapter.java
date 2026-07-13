package com.gymadmin.billing.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.billing.domain.model.Anulacion;
import com.gymadmin.billing.domain.model.EstadoAnulacion;
import com.gymadmin.billing.domain.port.out.AnulacionRepository;
import io.r2dbc.spi.Readable;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

/**
 * Adaptador R2DBC para {@code facturacion.anulaciones}. Sigue el mismo patrón
 * que {@code NotaCreditoReferenciaPersistenceAdapter} — SQL nativo por
 * {@link DatabaseClient} para tener control fino sobre los tipos y evitar la
 * complejidad de un {@code @Table} con parámetros anulables.
 * <p>
 * Todas las lecturas (excepto {@link #findById}) filtran por
 * {@code id_compania} — obligación multi-tenant. {@link #findById} entrega la
 * fila cruda porque el caller siempre valida el tenant antes de devolverla.
 */
@Component
@RequiredArgsConstructor
public class AnulacionPersistenceAdapter implements AnulacionRepository {

    private static final String INSERT_SQL = """
            INSERT INTO facturacion.anulaciones
              (id_compania, id_sucursal, id_comprobante, motivo, estado,
               id_comprobante_nc, id_usuario_solicita, id_usuario_aprueba,
               fecha_solicitud, fecha_resolucion, observacion_resolucion)
            VALUES (:idCompania, :idSucursal, :idComprobante, :motivo, :estado,
                    :idComprobanteNc, :idUsuarioSolicita, :idUsuarioAprueba,
                    COALESCE(:fechaSolicitud, NOW()), :fechaResolucion, :observacionResolucion)
            RETURNING id, id_compania, id_sucursal, id_comprobante, motivo, estado,
                      id_comprobante_nc, id_usuario_solicita, id_usuario_aprueba,
                      fecha_solicitud, fecha_resolucion, observacion_resolucion
            """;

    private static final String SELECT_BY_ID_SQL = """
            SELECT id, id_compania, id_sucursal, id_comprobante, motivo, estado,
                   id_comprobante_nc, id_usuario_solicita, id_usuario_aprueba,
                   fecha_solicitud, fecha_resolucion, observacion_resolucion
              FROM facturacion.anulaciones
             WHERE id = :id
            """;

    private static final String SELECT_BY_ID_COMPROBANTE_SQL = """
            SELECT id, id_compania, id_sucursal, id_comprobante, motivo, estado,
                   id_comprobante_nc, id_usuario_solicita, id_usuario_aprueba,
                   fecha_solicitud, fecha_resolucion, observacion_resolucion
              FROM facturacion.anulaciones
             WHERE id_comprobante = :idComprobante
               AND id_compania = :idCompania
             ORDER BY fecha_solicitud DESC
            """;

    private static final String SELECT_LIST_SQL = """
            SELECT id, id_compania, id_sucursal, id_comprobante, motivo, estado,
                   id_comprobante_nc, id_usuario_solicita, id_usuario_aprueba,
                   fecha_solicitud, fecha_resolucion, observacion_resolucion
              FROM facturacion.anulaciones
             WHERE id_compania = :idCompania
               AND (:idSucursal IS NULL OR id_sucursal = :idSucursal)
               AND (:estado IS NULL OR estado = :estado)
               AND (:idComprobante IS NULL OR id_comprobante = :idComprobante)
             ORDER BY fecha_solicitud DESC
             LIMIT :limit OFFSET :offset
            """;

    private static final String COUNT_SQL = """
            SELECT COUNT(*) AS c
              FROM facturacion.anulaciones
             WHERE id_compania = :idCompania
               AND (:idSucursal IS NULL OR id_sucursal = :idSucursal)
               AND (:estado IS NULL OR estado = :estado)
               AND (:idComprobante IS NULL OR id_comprobante = :idComprobante)
            """;

    private static final String UPDATE_ESTADO_SQL = """
            UPDATE facturacion.anulaciones
               SET estado = :estado,
                   id_usuario_aprueba = COALESCE(:idUsuarioAprueba, id_usuario_aprueba),
                   fecha_resolucion = :fechaResolucion,
                   observacion_resolucion = :observacionResolucion,
                   id_comprobante_nc = COALESCE(:idComprobanteNc, id_comprobante_nc)
             WHERE id = :id
             RETURNING id, id_compania, id_sucursal, id_comprobante, motivo, estado,
                       id_comprobante_nc, id_usuario_solicita, id_usuario_aprueba,
                       fecha_solicitud, fecha_resolucion, observacion_resolucion
            """;

    private final DatabaseClient databaseClient;

    @Override
    public Mono<Anulacion> save(Anulacion anulacion) {
        GenericExecuteSpec spec = databaseClient.sql(INSERT_SQL)
                .bind("idCompania", anulacion.getIdCompania())
                .bind("idSucursal", anulacion.getIdSucursal())
                .bind("idComprobante", anulacion.getIdComprobante())
                .bind("motivo", anulacion.getMotivo())
                .bind("estado", (anulacion.getEstado() != null ? anulacion.getEstado() : EstadoAnulacion.SOLICITADA).name())
                .bind("idUsuarioSolicita", anulacion.getIdUsuarioSolicita());
        spec = bindNullable(spec, "idComprobanteNc", anulacion.getIdComprobanteNc(), Long.class);
        spec = bindNullable(spec, "idUsuarioAprueba", anulacion.getIdUsuarioAprueba(), Integer.class);
        spec = bindNullable(spec, "fechaSolicitud", anulacion.getFechaSolicitud(), OffsetDateTime.class);
        spec = bindNullable(spec, "fechaResolucion", anulacion.getFechaResolucion(), OffsetDateTime.class);
        spec = bindNullable(spec, "observacionResolucion", anulacion.getObservacionResolucion(), String.class);
        return spec.map(AnulacionPersistenceAdapter::mapRow).one();
    }

    @Override
    public Mono<Anulacion> findById(Long id) {
        return databaseClient.sql(SELECT_BY_ID_SQL)
                .bind("id", id)
                .map(AnulacionPersistenceAdapter::mapRow)
                .one();
    }

    @Override
    public Flux<Anulacion> findByIdComprobante(Long idComprobante, Integer idCompania) {
        return databaseClient.sql(SELECT_BY_ID_COMPROBANTE_SQL)
                .bind("idComprobante", idComprobante)
                .bind("idCompania", idCompania)
                .map(AnulacionPersistenceAdapter::mapRow)
                .all();
    }

    @Override
    public Flux<Anulacion> findByEmpresa(Integer idCompania, Integer idSucursal, EstadoAnulacion estado,
                                          Long idComprobante, int offset, int limit) {
        GenericExecuteSpec spec = databaseClient.sql(SELECT_LIST_SQL)
                .bind("idCompania", idCompania)
                .bind("offset", offset)
                .bind("limit", limit);
        spec = bindNullable(spec, "idSucursal", idSucursal, Integer.class);
        spec = bindNullable(spec, "estado", estado != null ? estado.name() : null, String.class);
        spec = bindNullable(spec, "idComprobante", idComprobante, Long.class);
        return spec.map(AnulacionPersistenceAdapter::mapRow).all();
    }

    @Override
    public Mono<Long> countByEmpresa(Integer idCompania, Integer idSucursal, EstadoAnulacion estado, Long idComprobante) {
        GenericExecuteSpec spec = databaseClient.sql(COUNT_SQL)
                .bind("idCompania", idCompania);
        spec = bindNullable(spec, "idSucursal", idSucursal, Integer.class);
        spec = bindNullable(spec, "estado", estado != null ? estado.name() : null, String.class);
        spec = bindNullable(spec, "idComprobante", idComprobante, Long.class);
        return spec.map(row -> row.get("c", Long.class)).one();
    }

    @Override
    public Mono<Anulacion> updateEstado(Long id,
                                        EstadoAnulacion nuevoEstado,
                                        Integer idUsuarioAprueba,
                                        OffsetDateTime fechaResolucion,
                                        String observacionResolucion,
                                        Long idComprobanteNc) {
        GenericExecuteSpec spec = databaseClient.sql(UPDATE_ESTADO_SQL)
                .bind("id", id)
                .bind("estado", nuevoEstado.name());
        spec = bindNullable(spec, "idUsuarioAprueba", idUsuarioAprueba, Integer.class);
        spec = bindNullable(spec, "fechaResolucion", fechaResolucion, OffsetDateTime.class);
        spec = bindNullable(spec, "observacionResolucion", observacionResolucion, String.class);
        spec = bindNullable(spec, "idComprobanteNc", idComprobanteNc, Long.class);
        return spec.map(AnulacionPersistenceAdapter::mapRow).one();
    }

    private static <T> GenericExecuteSpec bindNullable(GenericExecuteSpec spec, String name, T value, Class<T> type) {
        return value != null ? spec.bind(name, value) : spec.bindNull(name, type);
    }

    private static Anulacion mapRow(Readable row) {
        String estado = row.get("estado", String.class);
        return Anulacion.builder()
                .id(row.get("id", Long.class))
                .idCompania(row.get("id_compania", Integer.class))
                .idSucursal(row.get("id_sucursal", Integer.class))
                .idComprobante(row.get("id_comprobante", Long.class))
                .motivo(row.get("motivo", String.class))
                .estado(estado != null ? EstadoAnulacion.valueOf(estado.trim()) : null)
                .idComprobanteNc(row.get("id_comprobante_nc", Long.class))
                .idUsuarioSolicita(row.get("id_usuario_solicita", Integer.class))
                .idUsuarioAprueba(row.get("id_usuario_aprueba", Integer.class))
                .fechaSolicitud(row.get("fecha_solicitud", OffsetDateTime.class))
                .fechaResolucion(row.get("fecha_resolucion", OffsetDateTime.class))
                .observacionResolucion(row.get("observacion_resolucion", String.class))
                .build();
    }
}
