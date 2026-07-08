package com.gymadmin.auth.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.auth.domain.port.out.BitacoraPort;
import com.gymadmin.auth.dto.response.BitacoraPagedResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class BitacoraPersistenceAdapter implements BitacoraPort {

    private final DatabaseClient db;
    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> save(Integer idCompania, Integer idSucursal, Integer idUsuario,
                           String modulo, String accion, Integer entidadId,
                           Map<String, Object> detalle, String ip) {
        String detalleJson = toJson(detalle);
        OffsetDateTime now = OffsetDateTime.now();
        return db.sql("""
                        INSERT INTO seguridad.bitacora_accesos
                        (id_compania, id_sucursal, id_usuario, modulo, accion, entidad_id,
                         detalle, ip, fecha, creacion_fecha, creacion_usuario)
                        VALUES (:idCompania, :idSucursal, :idUsuario, :modulo, :accion, :entidadId,
                                :detalle::jsonb, :ip, :fecha, :fecha, 'sistema')
                        """)
                .bind("idCompania", idCompania)
                .bind("idSucursal", idSucursal)
                .bind("idUsuario", idUsuario != null ? idUsuario : 0)
                .bind("modulo", modulo)
                .bind("accion", accion)
                .bind("entidadId", entidadId != null ? entidadId : 0)
                .bind("detalle", detalleJson != null ? detalleJson : "{}")
                .bind("ip", ip != null ? ip : "")
                .bind("fecha", now)
                .fetch().rowsUpdated()
                .then();
    }

    @Override
    public Mono<BitacoraPagedResponse> findWithFilters(Integer idCompania, String modulo,
                                                       OffsetDateTime desde, OffsetDateTime hasta,
                                                       Integer idUsuario, int pagina, int limit) {
        StringBuilder dataSql = new StringBuilder("""
                SELECT b.id, b.id_usuario, p.nombre AS nombre_usuario,
                       b.modulo, b.accion, b.entidad_id, b.ip, b.fecha
                FROM seguridad.bitacora_accesos b
                LEFT JOIN seguridad.usuarios u
                       ON b.id_usuario = u.id AND b.id_compania = u.id_compania
                LEFT JOIN identidad.personas p ON u.id_persona = p.id
                WHERE b.id_compania = :idCompania
                """);

        StringBuilder countSql = new StringBuilder("""
                SELECT COUNT(*) FROM seguridad.bitacora_accesos b
                WHERE b.id_compania = :idCompania
                """);

        List<String> conditions = new ArrayList<>();
        if (modulo != null)    conditions.add("b.modulo = :modulo");
        if (desde != null)     conditions.add("b.fecha >= :desde");
        if (hasta != null)     conditions.add("b.fecha <= :hasta");
        if (idUsuario != null) conditions.add("b.id_usuario = :idUsuario");

        conditions.forEach(c -> {
            dataSql.append(" AND ").append(c);
            countSql.append(" AND ").append(c);
        });

        dataSql.append(" ORDER BY b.fecha DESC LIMIT :limit OFFSET :offset");

        int offset = (pagina - 1) * limit;

        DatabaseClient.GenericExecuteSpec bindCommon = db.sql(dataSql.toString())
                .bind("idCompania", idCompania)
                .bind("limit", limit)
                .bind("offset", offset);
        bindCommon = bindOptional(bindCommon, "modulo", modulo, String.class);
        bindCommon = bindOptional(bindCommon, "desde", desde, OffsetDateTime.class);
        bindCommon = bindOptional(bindCommon, "hasta", hasta, OffsetDateTime.class);
        bindCommon = bindOptional(bindCommon, "idUsuario", idUsuario, Integer.class);

        DatabaseClient.GenericExecuteSpec bindCount = db.sql(countSql.toString())
                .bind("idCompania", idCompania);
        bindCount = bindOptional(bindCount, "modulo", modulo, String.class);
        bindCount = bindOptional(bindCount, "desde", desde, OffsetDateTime.class);
        bindCount = bindOptional(bindCount, "hasta", hasta, OffsetDateTime.class);
        bindCount = bindOptional(bindCount, "idUsuario", idUsuario, Integer.class);

        Mono<List<BitacoraPagedResponse.EntryDto>> dataList = bindCommon
                .map((row, meta) -> new BitacoraPagedResponse.EntryDto(
                        row.get("id", Long.class),
                        row.get("id_usuario", Integer.class),
                        row.get("nombre_usuario", String.class),
                        row.get("modulo", String.class),
                        row.get("accion", String.class),
                        row.get("entidad_id", Integer.class),
                        row.get("ip", String.class),
                        row.get("fecha", OffsetDateTime.class)))
                .all()
                .collectList();

        Mono<Long> total = bindCount
                .map((row, meta) -> row.get(0, Long.class))
                .one()
                .defaultIfEmpty(0L);

        return Mono.zip(total, dataList)
                .map(t -> new BitacoraPagedResponse(t.getT1(), pagina, t.getT2()));
    }

    private <T> DatabaseClient.GenericExecuteSpec bindOptional(
            DatabaseClient.GenericExecuteSpec spec, String name, T value, Class<T> type) {
        return value != null ? spec.bind(name, value) : spec;
    }

    private String toJson(Map<String, Object> map) {
        if (map == null) return "{}";
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
