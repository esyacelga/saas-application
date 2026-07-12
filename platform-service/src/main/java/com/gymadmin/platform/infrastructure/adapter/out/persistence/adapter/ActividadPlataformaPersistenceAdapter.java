package com.gymadmin.platform.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.platform.domain.model.ActividadPlataforma;
import com.gymadmin.platform.domain.port.in.ActividadPlataformaUseCase;
import com.gymadmin.platform.domain.port.out.ActividadPlataformaRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.ActividadPlataformaEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.ActividadPlataformaR2dbcRepository;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class ActividadPlataformaPersistenceAdapter implements ActividadPlataformaRepository {

    private final ActividadPlataformaR2dbcRepository r2dbcRepository;
    private final DatabaseClient databaseClient;

    public ActividadPlataformaPersistenceAdapter(ActividadPlataformaR2dbcRepository r2dbcRepository,
                                                  DatabaseClient databaseClient) {
        this.r2dbcRepository = r2dbcRepository;
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<Void> save(ActividadPlataforma actividad) {
        // REQ-SAAS-001 Sub-fase 1.6 item #5: la columna `detalle` es JSONB y
        // R2DBC no aplica cast implícito text→jsonb en INSERT. Del mismo modo,
        // `ip` es INET. Persistimos con SQL explícito y CAST para evitar
        // BadSqlGrammarException cuando el evento trae detalle o ip no nulos.
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(
                "INSERT INTO saas.actividad_plataforma (" +
                "tipo_evento, modulo, entidad_id, entidad_nombre, detalle, usuario, " +
                "ip, fecha, id_compania, id_usuario_actor, tipo_actor) " +
                "VALUES (:tipoEvento, :modulo, :entidadId, :entidadNombre, " +
                "CAST(:detalle AS jsonb), :usuario, CAST(:ip AS inet), :fecha, " +
                ":idCompania, :idUsuarioActor, :tipoActor)")
                .bind("tipoEvento", actividad.getTipoEvento() != null ? actividad.getTipoEvento() : "")
                .bind("modulo", actividad.getModulo() != null ? actividad.getModulo() : "suscripciones")
                .bind("usuario", actividad.getUsuario() != null ? actividad.getUsuario() : "sistema")
                .bind("fecha", actividad.getFecha() != null ? actividad.getFecha() : OffsetDateTime.now())
                .bind("tipoActor", actividad.getTipoActor() != null ? actividad.getTipoActor().name() : "SISTEMA");
        spec = bindNullable(spec, "entidadId", actividad.getEntidadId(), Long.class);
        spec = bindNullable(spec, "entidadNombre", actividad.getEntidadNombre(), String.class);
        spec = bindNullable(spec, "detalle", actividad.getDetalle(), String.class);
        spec = bindNullable(spec, "ip", actividad.getIp(), String.class);
        spec = bindNullable(spec, "idCompania", actividad.getIdCompania(), Long.class);
        spec = bindNullable(spec, "idUsuarioActor", actividad.getIdUsuarioActor(), Long.class);
        return spec.fetch().rowsUpdated().then();
    }

    private <T> DatabaseClient.GenericExecuteSpec bindNullable(DatabaseClient.GenericExecuteSpec spec,
                                                                String name, T value, Class<T> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    @Override
    public Flux<ActividadPlataforma> findAll(ActividadPlataformaUseCase.ListarQuery query) {
        StringBuilder sql = new StringBuilder(
                "SELECT * FROM saas.actividad_plataforma WHERE 1=1");
        List<Object[]> bindings = buildBindings(sql, query);
        sql.append(" ORDER BY fecha DESC");
        sql.append(" LIMIT ").append(query.porPagina());
        sql.append(" OFFSET ").append((long) (query.pagina() - 1) * query.porPagina());

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql.toString());
        for (Object[] binding : bindings) {
            spec = spec.bind((String) binding[0], binding[1]);
        }

        return spec.map((row, meta) -> {
            ActividadPlataforma a = new ActividadPlataforma();
            a.setId(row.get("id", Long.class));
            a.setTipoEvento(row.get("tipo_evento", String.class));
            a.setModulo(row.get("modulo", String.class));
            a.setEntidadId(row.get("entidad_id", Long.class));
            a.setEntidadNombre(row.get("entidad_nombre", String.class));
            a.setDetalle(row.get("detalle", String.class));
            a.setUsuario(row.get("usuario", String.class));
            a.setIp(row.get("ip", String.class));
            a.setFecha(row.get("fecha", OffsetDateTime.class));
            // REQ-SAAS-001 sección 6bis — nuevas columnas post-migración GYM-003.
            a.setIdCompania(row.get("id_compania", Long.class));
            a.setIdUsuarioActor(row.get("id_usuario_actor", Long.class));
            String tipoActor = row.get("tipo_actor", String.class);
            if (tipoActor != null) {
                a.setTipoActor(ActividadPlataforma.TipoActor.valueOf(tipoActor.toUpperCase()));
            }
            return a;
        }).all();
    }

    @Override
    public Mono<Long> count(ActividadPlataformaUseCase.ListarQuery query) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM saas.actividad_plataforma WHERE 1=1");
        List<Object[]> bindings = buildBindings(sql, query);

        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql.toString());
        for (Object[] binding : bindings) {
            spec = spec.bind((String) binding[0], binding[1]);
        }

        return spec.map((row, meta) -> row.get(0, Long.class)).one();
    }

    private List<Object[]> buildBindings(StringBuilder sql, ActividadPlataformaUseCase.ListarQuery query) {
        List<Object[]> bindings = new ArrayList<>();
        if (query.modulo() != null && !query.modulo().isBlank()) {
            sql.append(" AND modulo = :modulo");
            bindings.add(new Object[]{"modulo", query.modulo()});
        }
        if (query.tipoEvento() != null && !query.tipoEvento().isBlank()) {
            sql.append(" AND tipo_evento = :tipoEvento");
            bindings.add(new Object[]{"tipoEvento", query.tipoEvento()});
        }
        if (query.desde() != null && !query.desde().isBlank()) {
            sql.append(" AND fecha >= :desde::timestamptz");
            bindings.add(new Object[]{"desde", query.desde()});
        }
        if (query.hasta() != null && !query.hasta().isBlank()) {
            sql.append(" AND fecha <= :hasta::timestamptz + interval '1 day'");
            bindings.add(new Object[]{"hasta", query.hasta()});
        }
        return bindings;
    }

    private ActividadPlataformaEntity toEntity(ActividadPlataforma a) {
        ActividadPlataformaEntity e = new ActividadPlataformaEntity();
        e.setTipoEvento(a.getTipoEvento());
        e.setModulo(a.getModulo());
        e.setEntidadId(a.getEntidadId());
        e.setEntidadNombre(a.getEntidadNombre());
        e.setDetalle(a.getDetalle());
        e.setUsuario(a.getUsuario());
        e.setIp(a.getIp());
        e.setFecha(a.getFecha());
        // REQ-SAAS-001 sección 6bis — nuevas columnas.
        e.setIdCompania(a.getIdCompania());
        e.setIdUsuarioActor(a.getIdUsuarioActor());
        if (a.getTipoActor() != null) {
            e.setTipoActor(a.getTipoActor().name());
        }
        return e;
    }
}
