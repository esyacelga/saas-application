package com.gymadmin.finance.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.finance.infrastructure.adapter.out.persistence.entity.EgresoEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface EgresoR2dbcRepository extends ReactiveCrudRepository<EgresoEntity, Integer> {

    @Query("""
            SELECT * FROM finanzas.egresos
            WHERE id_compania = :idCompania
              AND eliminado = false
              AND (:desde IS NULL OR fecha >= :desde)
              AND (:hasta IS NULL OR fecha <= :hasta)
              AND (:idCategoria IS NULL OR id_categoria = :idCategoria)
            ORDER BY fecha DESC, id DESC
            LIMIT :limit OFFSET :offset
            """)
    Flux<EgresoEntity> findByFilters(Integer idCompania, LocalDate desde, LocalDate hasta,
                                     Integer idCategoria, int limit, long offset);

    @Query("""
            SELECT COUNT(*) FROM finanzas.egresos
            WHERE id_compania = :idCompania
              AND eliminado = false
              AND (:desde IS NULL OR fecha >= :desde)
              AND (:hasta IS NULL OR fecha <= :hasta)
              AND (:idCategoria IS NULL OR id_categoria = :idCategoria)
            """)
    Mono<Long> countByFilters(Integer idCompania, LocalDate desde, LocalDate hasta, Integer idCategoria);

    @Query("""
            SELECT COALESCE(SUM(monto), 0) FROM finanzas.egresos
            WHERE id_compania = :idCompania
              AND eliminado = false
              AND (:desde IS NULL OR fecha >= :desde)
              AND (:hasta IS NULL OR fecha <= :hasta)
              AND (:idCategoria IS NULL OR id_categoria = :idCategoria)
            """)
    Mono<BigDecimal> sumByFilters(Integer idCompania, LocalDate desde, LocalDate hasta, Integer idCategoria);
}
