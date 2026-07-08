package com.gymadmin.platform.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.PermisoEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface PermisoR2dbcRepository extends ReactiveCrudRepository<PermisoEntity, Long> {

    Flux<PermisoEntity> findAllByIdCompaniaAndEliminadoFalse(Long idCompania);
}
