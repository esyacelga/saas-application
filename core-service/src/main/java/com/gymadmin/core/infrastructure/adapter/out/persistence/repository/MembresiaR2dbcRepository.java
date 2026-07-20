package com.gymadmin.core.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.core.infrastructure.adapter.out.persistence.entity.MembresiaEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MembresiaR2dbcRepository extends ReactiveCrudRepository<MembresiaEntity, Long> {

    /**
     * Historial completo de membresías del cliente (multi-tenant safe).
     * <b>Incluye</b> membresías con {@code eliminado = true} — GYM-003 requiere mostrarlas
     * en la PWA con badge de rechazo y motivo. Filtra por {@code id_compania} para
     * evitar exponer datos cross-tenant.
     */
    @Query("""
        SELECT * FROM core.membresias
        WHERE id_cliente = :idCliente
          AND id_compania = :idCompania
        ORDER BY creacion_fecha DESC
        """)
    Flux<MembresiaEntity> findAllByIdClienteAndIdCompania(Long idCliente, Long idCompania);

    @Query("""
        SELECT * FROM core.membresias
        WHERE id_cliente = :idCliente
          AND id_compania = :idCompania
          AND estado = 'activa'
          AND estado_pago = 'PAGADO'
          AND eliminado = false
        ORDER BY creacion_fecha DESC
        LIMIT 1
        """)
    Mono<MembresiaEntity> findActivaByIdClienteAndIdCompania(Long idCliente, Long idCompania);

    @Query("""
        SELECT * FROM core.membresias
        WHERE id_cliente = :idCliente
          AND id_compania = :idCompania
          AND estado_pago = 'PENDIENTE'
          AND eliminado = false
        ORDER BY creacion_fecha DESC
        LIMIT 1
        """)
    Mono<MembresiaEntity> findPendienteVivaByIdCliente(Long idCliente, Long idCompania);

    @Query("""
        SELECT * FROM core.membresias
        WHERE id_compania = :idCompania
          AND estado_pago = 'PENDIENTE'
          AND eliminado = false
        ORDER BY creacion_fecha DESC
        """)
    Flux<MembresiaEntity> findPendientesPorCompania(Long idCompania);

    /**
     * Conteo agrupado por {@code origen} de las membresías PENDIENTE + vivas de la compañía.
     * Retorna 0-2 filas (cliente y/o staff). El adapter agrega el mapa; si algún origen
     * no aparece se toma como 0. Usa el índice parcial
     * {@code idx_membresias_pendientes_cliente} (cubre el filtro
     * {@code estado_pago='PENDIENTE' AND origen='cliente' AND eliminado=false}).
     */
    @Query("""
        SELECT origen, COUNT(*) AS total
        FROM core.membresias
        WHERE id_compania = :idCompania
          AND estado_pago = 'PENDIENTE'
          AND eliminado = false
        GROUP BY origen
        """)
    Flux<PendienteOrigenRow> contarPendientesPorOrigen(Long idCompania);

    /**
     * Proyección plana para el conteo agrupado por origen. Los nombres de columna
     * coinciden con los alias del SELECT anterior.
     */
    record PendienteOrigenRow(String origen, Long total) {}

    @Query("""
        SELECT * FROM core.membresias
        WHERE id_cliente = :idCliente
          AND id_compania = :idCompania
          AND eliminado = true
        ORDER BY fecha_eliminacion DESC NULLS LAST, creacion_fecha DESC
        LIMIT 1
        """)
    Mono<MembresiaEntity> findUltimaRechazadaByIdCliente(Long idCliente, Long idCompania);

    @Query("""
        SELECT COUNT(*) FROM asistencia.asistencias
        WHERE id_membresia = :idMembresia
        """)
    Mono<Long> countAsistenciasByIdMembresia(Long idMembresia);

    @Query("""
        SELECT * FROM core.membresias
        WHERE estado = 'activa'
          AND estado_pago = 'PAGADO'
          AND eliminado = false
        """)
    Flux<MembresiaEntity> findActivasParaJob();
}
