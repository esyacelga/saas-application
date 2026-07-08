package com.gymadmin.auth.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.RolEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface RolR2dbcRepository extends ReactiveCrudRepository<RolEntity, Integer> {
    Flux<RolEntity> findByIdCompania(Integer idCompania);
    Mono<RolEntity> findByIdAndIdCompania(Integer id, Integer idCompania);
    Mono<Boolean> existsByIdCompaniaAndNombre(Integer idCompania, String nombre);
}
