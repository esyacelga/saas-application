package com.gymadmin.platform.domain.port.out;

import com.gymadmin.platform.domain.model.Plan;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PlanRepository {

    Flux<Plan> findAll();

    Mono<Plan> findById(Long id);

    Mono<Plan> save(Plan plan);

    Mono<Plan> update(Plan plan);

    Mono<Void> deleteCaracteristicasByPlanId(Long planId);

    Mono<Void> saveCaracteristicaRelations(Long planId, java.util.List<Long> caracteristicaIds);

    Flux<Plan> findAllWithCaracteristicas();

    Mono<Plan> findByIdWithCaracteristicas(Long id);
}
