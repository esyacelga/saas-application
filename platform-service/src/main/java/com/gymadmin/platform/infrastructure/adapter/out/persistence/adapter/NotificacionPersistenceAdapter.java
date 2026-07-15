package com.gymadmin.platform.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.platform.domain.model.NotificacionSuscripcion;
import com.gymadmin.platform.domain.port.out.NotificacionRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.NotificacionSuscripcionEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.NotificacionR2dbcRepository;
import io.r2dbc.spi.Readable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

@Component
public class NotificacionPersistenceAdapter implements NotificacionRepository {

    private final NotificacionR2dbcRepository repository;
    private final DatabaseClient databaseClient;

    public NotificacionPersistenceAdapter(NotificacionR2dbcRepository repository,
                                           DatabaseClient databaseClient) {
        this.repository = repository;
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<NotificacionSuscripcion> save(NotificacionSuscripcion notificacion) {
        return repository.save(toEntity(notificacion)).map(this::toDomain);
    }

    @Override
    public Flux<NotificacionSuscripcion> findByIdCompaniaPlan(Long idCompaniaPlan) {
        return repository.findByIdCompaniaPlan(idCompaniaPlan).map(this::toDomain);
    }

    @Override
    public Mono<Boolean> existsByIdCompaniaPlanAndDiasAntes(Long idCompaniaPlan, Integer diasAntes) {
        return repository.existsByIdCompaniaPlanAndDiasAntes(idCompaniaPlan, diasAntes);
    }

    @Override
    public Mono<Boolean> existsIdempotente(Long idCompaniaPlan, String tipo, String canal, Integer diasAntes) {
        return databaseClient.sql("""
                SELECT EXISTS(
                    SELECT 1 FROM tenant.notificaciones_suscripcion
                    WHERE id_compania_plan = :idCompaniaPlan
                      AND tipo = :tipo
                      AND canal = :canal
                      AND dias_antes >= :diasAntes
                      AND estado <> 'fallido'
                )
                """)
                .bind("idCompaniaPlan", idCompaniaPlan)
                .bind("tipo", tipo)
                .bind("canal", canal)
                .bind("diasAntes", diasAntes)
                .map((row, meta) -> row.get(0, Boolean.class))
                .one();
    }

    @Override
    public Flux<NotificacionSuscripcion> findBannersActivosHoy(Long idCompania) {
        return databaseClient.sql("""
                SELECT * FROM tenant.notificaciones_suscripcion
                WHERE id_compania = :idCompania
                  AND canal = 'banner'
                  AND estado <> 'fallido'
                  AND (descartado_at IS NULL OR descartado_at::date < CURRENT_DATE)
                ORDER BY dias_antes ASC, creacion_fecha DESC
                """)
                .bind("idCompania", idCompania)
                .map((row, meta) -> mapRow(row))
                .all();
    }

    @Override
    public Mono<NotificacionSuscripcion> findById(Long id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Mono<Long> descartarBanner(Long idBanner, Long idCompania) {
        return databaseClient.sql("""
                UPDATE tenant.notificaciones_suscripcion
                SET descartado_at = NOW(), modifica_fecha = NOW()
                WHERE id = :id
                  AND id_compania = :idCompania
                  AND canal = 'banner'
                """)
                .bind("id", idBanner)
                .bind("idCompania", idCompania)
                .fetch()
                .rowsUpdated();
    }

    @Override
    public Flux<NotificacionSuscripcion> claimLoteEmails(int max) {
        // Claim atómico: UPDATE ... WHERE id IN (SELECT ... FOR UPDATE SKIP LOCKED)
        // marca las filas y las devuelve para procesamiento inmediato.
        // Sin estado 'procesando' — el worker las devuelve directo a 'enviado',
        // 'reintentar' o 'fallido' en la misma transacción; el lock del SELECT
        // vive hasta el commit de este UPDATE.
        String sql = """
                UPDATE tenant.notificaciones_suscripcion
                SET modifica_fecha = NOW()
                WHERE id IN (
                    SELECT id FROM tenant.notificaciones_suscripcion
                    WHERE canal = 'email'
                      AND estado IN ('pendiente','reintentar')
                      AND (proximo_intento IS NULL OR proximo_intento <= NOW())
                    ORDER BY creacion_fecha
                    LIMIT %d
                    FOR UPDATE SKIP LOCKED
                )
                RETURNING id, id_compania, id_compania_plan, tipo, dias_antes, canal,
                          estado, intentos, ultimo_error, proximo_intento, descartado_at, fecha_envio
                """.formatted(max);
        return databaseClient.sql(sql)
                .map((row, meta) -> mapRow(row))
                .all();
    }

    @Override
    public Flux<NotificacionSuscripcion> claimLoteWhatsapp(int max) {
        // REQ-SAAS-001 (Fase 3): idéntico a claimLoteEmails pero para canal='whatsapp'.
        // Mismo claim atómico con FOR UPDATE SKIP LOCKED — el worker devuelve la fila
        // a 'enviado', 'reintentar' o 'fallido' en la misma transacción.
        String sql = """
                UPDATE tenant.notificaciones_suscripcion
                SET modifica_fecha = NOW()
                WHERE id IN (
                    SELECT id FROM tenant.notificaciones_suscripcion
                    WHERE canal = 'whatsapp'
                      AND estado IN ('pendiente','reintentar')
                      AND (proximo_intento IS NULL OR proximo_intento <= NOW())
                    ORDER BY creacion_fecha
                    LIMIT %d
                    FOR UPDATE SKIP LOCKED
                )
                RETURNING id, id_compania, id_compania_plan, tipo, dias_antes, canal,
                          estado, intentos, ultimo_error, proximo_intento, descartado_at, fecha_envio
                """.formatted(max);
        return databaseClient.sql(sql)
                .map((row, meta) -> mapRow(row))
                .all();
    }

    @Override
    public Mono<Void> marcarEnviado(Long id) {
        return databaseClient.sql("""
                UPDATE tenant.notificaciones_suscripcion
                SET estado = 'enviado', fecha_envio = NOW(), modifica_fecha = NOW()
                WHERE id = :id
                """)
                .bind("id", id)
                .fetch()
                .rowsUpdated()
                .then();
    }

    @Override
    public Mono<Void> marcarReintentar(Long id, int intentos, String ultimoError, OffsetDateTime proximoIntento) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                UPDATE tenant.notificaciones_suscripcion
                SET estado = 'reintentar',
                    intentos = :intentos,
                    ultimo_error = :ultimoError,
                    proximo_intento = :proximoIntento,
                    modifica_fecha = NOW()
                WHERE id = :id
                """)
                .bind("id", id)
                .bind("intentos", intentos)
                .bind("proximoIntento", proximoIntento);
        spec = (ultimoError == null)
                ? spec.bindNull("ultimoError", String.class)
                : spec.bind("ultimoError", ultimoError);
        return spec.fetch().rowsUpdated().then();
    }

    @Override
    public Mono<Void> marcarFallido(Long id, String ultimoError) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql("""
                UPDATE tenant.notificaciones_suscripcion
                SET estado = 'fallido',
                    ultimo_error = :ultimoError,
                    modifica_fecha = NOW()
                WHERE id = :id
                """)
                .bind("id", id);
        spec = (ultimoError == null)
                ? spec.bindNull("ultimoError", String.class)
                : spec.bind("ultimoError", ultimoError);
        return spec.fetch().rowsUpdated().then();
    }

    private NotificacionSuscripcion mapRow(Readable row) {
        NotificacionSuscripcion n = new NotificacionSuscripcion();
        n.setId(row.get("id", Long.class));
        n.setIdCompania(row.get("id_compania", Long.class));
        n.setIdCompaniaPlan(row.get("id_compania_plan", Long.class));
        n.setTipo(row.get("tipo", String.class));
        n.setDiasAntes(row.get("dias_antes", Integer.class));
        n.setCanal(row.get("canal", String.class));
        n.setEstado(row.get("estado", String.class));
        n.setIntentos(row.get("intentos", Integer.class));
        n.setUltimoError(row.get("ultimo_error", String.class));
        n.setProximoIntento(row.get("proximo_intento", OffsetDateTime.class));
        n.setDescartadoAt(row.get("descartado_at", OffsetDateTime.class));
        n.setFechaEnvio(row.get("fecha_envio", LocalDateTime.class));
        return n;
    }

    private NotificacionSuscripcion toDomain(NotificacionSuscripcionEntity entity) {
        NotificacionSuscripcion n = new NotificacionSuscripcion();
        n.setId(entity.getId());
        n.setIdCompania(entity.getIdCompania());
        n.setIdCompaniaPlan(entity.getIdCompaniaPlan());
        n.setTipo(entity.getTipo());
        n.setDiasAntes(entity.getDiasAntes());
        n.setCanal(entity.getCanal());
        n.setEstado(entity.getEstado());
        n.setIntentos(entity.getIntentos());
        n.setUltimoError(entity.getUltimoError());
        n.setProximoIntento(entity.getProximoIntento());
        n.setDescartadoAt(entity.getDescartadoAt());
        n.setFechaEnvio(entity.getFechaEnvio());
        return n;
    }

    private NotificacionSuscripcionEntity toEntity(NotificacionSuscripcion n) {
        NotificacionSuscripcionEntity entity = new NotificacionSuscripcionEntity();
        entity.setId(n.getId());
        entity.setIdCompania(n.getIdCompania());
        entity.setIdCompaniaPlan(n.getIdCompaniaPlan());
        entity.setTipo(n.getTipo());
        entity.setDiasAntes(n.getDiasAntes());
        entity.setCanal(n.getCanal());
        entity.setEstado(n.getEstado());
        entity.setIntentos(n.getIntentos());
        entity.setUltimoError(n.getUltimoError());
        entity.setProximoIntento(n.getProximoIntento());
        entity.setDescartadoAt(n.getDescartadoAt());
        entity.setFechaEnvio(n.getFechaEnvio());
        return entity;
    }
}
