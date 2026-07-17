package com.gymadmin.core.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.core.infrastructure.adapter.out.persistence.entity.MembresiaEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface MembresiaR2dbcRepository extends ReactiveCrudRepository<MembresiaEntity, Long> {

    @Query("""
        SELECT * FROM core.membresias
        WHERE id_cliente = :idCliente
          AND eliminado = false
        ORDER BY creacion_fecha DESC
        """)
    Flux<MembresiaEntity> findByIdCliente(Long idCliente);

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
