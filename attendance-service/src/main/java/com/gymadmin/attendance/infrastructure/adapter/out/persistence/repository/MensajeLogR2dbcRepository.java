package com.gymadmin.attendance.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.attendance.infrastructure.adapter.out.persistence.entity.MensajeLogEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

public interface MensajeLogR2dbcRepository extends ReactiveCrudRepository<MensajeLogEntity, Long> {

    @Query("""
            SELECT * FROM asistencia.mensajes_log
            WHERE id_compania = :idCompania
              AND eliminado = false
              AND (:idCliente IS NULL OR id_cliente = :idCliente)
              AND (:tipo IS NULL OR tipo = :tipo)
              AND (:estado IS NULL OR estado = :estado)
              AND (:desde IS NULL OR fecha_programada >= :desde)
            ORDER BY creacion_fecha DESC
            """)
    Flux<MensajeLogEntity> findByFiltros(Integer idCompania, Integer idCliente,
                                          String tipo, String estado, OffsetDateTime desde);

    @Query("""
            SELECT COUNT(*) FROM asistencia.mensajes_log
            WHERE id_cliente = :idCliente
              AND tipo = :tipo
              AND fecha_programada >= :desde
              AND eliminado = false
            """)
    Mono<Long> countByClienteAndTipoDesde(Integer idCliente, String tipo, OffsetDateTime desde);

    /**
     * REQ-SAAS-001 (Fase 5, issue C2): idempotencia de avisos de vencimiento por
     * {@code (idCliente, tipo, canal, día)}. A diferencia de {@code countByClienteAndTipoDesde}
     * (anti-spam de ausencia, atado a la última asistencia), esta clave garantiza <b>un solo aviso
     * por bucket/canal por día</b> — un reinicio del job el mismo día no duplica (Meta bloquea el
     * número si hay duplicados). El rango [{@code desde}, {@code hasta}) delimita el día de negocio.
     */
    @Query("""
            SELECT COUNT(*) > 0 FROM asistencia.mensajes_log
            WHERE id_cliente = :idCliente
              AND tipo = :tipo
              AND canal = :canal
              AND fecha_programada >= :desde
              AND fecha_programada < :hasta
              AND eliminado = false
            """)
    Mono<Boolean> existsEnviadoEnRango(Integer idCliente, String tipo, String canal,
                                       OffsetDateTime desde, OffsetDateTime hasta);
}
