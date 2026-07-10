package com.gymadmin.platform.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.PlanEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PlanR2dbcRepository extends ReactiveCrudRepository<PlanEntity, Long> {

    Flux<PlanEntity> findByActivoTrue();

    @Query("SELECT * FROM saas.planes WHERE codigo = :codigo ORDER BY activo DESC, id ASC LIMIT 1")
    Mono<PlanEntity> findByCodigo(String codigo);

    @Query("SELECT * FROM saas.planes WHERE activo = TRUE AND (es_legacy IS NULL OR es_legacy = FALSE) ORDER BY id ASC")
    Flux<PlanEntity> findByActivoTrueAndEsLegacyFalse();
}
