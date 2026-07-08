package com.gymadmin.platform.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.RolEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface RolR2dbcRepository extends ReactiveCrudRepository<RolEntity, Long> {

    Mono<RolEntity> findByIdCompaniaAndNombreAndEliminadoFalse(Long idCompania, String nombre);
}
