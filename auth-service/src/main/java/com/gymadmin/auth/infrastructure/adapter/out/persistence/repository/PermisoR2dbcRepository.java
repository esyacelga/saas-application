package com.gymadmin.auth.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.PermisoEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

public interface PermisoR2dbcRepository extends ReactiveCrudRepository<PermisoEntity, Integer> {
    Flux<PermisoEntity> findByIdCompania(Integer idCompania);
    Flux<PermisoEntity> findByIdInAndIdCompania(Collection<Integer> ids, Integer idCompania);
    Mono<PermisoEntity> findByIdAndEliminadoFalse(Integer id);
    Mono<Boolean> existsByIdCompaniaAndNombreAndEliminadoFalse(Integer idCompania, String nombre);
}
