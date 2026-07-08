package com.gymadmin.platform.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.PlanEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface PlanR2dbcRepository extends ReactiveCrudRepository<PlanEntity, Long> {

    Flux<PlanEntity> findByActivoTrue();
}
