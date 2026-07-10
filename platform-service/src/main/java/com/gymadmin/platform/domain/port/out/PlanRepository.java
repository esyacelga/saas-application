package com.gymadmin.platform.domain.port.out;

import com.gymadmin.platform.domain.model.Plan;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PlanRepository {

    Flux<Plan> findAll();

    Mono<Plan> findById(Long id);

    /**
     * REQ-SAAS-001 — busca por {@code codigo} (unique en {@code saas.planes}).
     * Devuelve el primero activo si hay varios. Emite {@code Mono.empty()} si no
     * hay coincidencia.
     */
    Mono<Plan> findByCodigo(String codigo);

    /**
     * REQ-SAAS-001 — planes publicables en la landing: {@code activo=true} y
     * {@code es_legacy=false}. Incluye Free, Trial, Premium; excluye planes
     * históricos y grandfathered.
     */
    Flux<Plan> findByActivoTrueAndEsLegacyFalse();

    Mono<Plan> save(Plan plan);

    Mono<Plan> update(Plan plan);

    Mono<Void> deleteCaracteristicasByPlanId(Long planId);

    Mono<Void> saveCaracteristicaRelations(Long planId, java.util.List<Long> caracteristicaIds);

    Flux<Plan> findAllWithCaracteristicas();

    Mono<Plan> findByIdWithCaracteristicas(Long id);
}
