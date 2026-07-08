package com.gymadmin.platform.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.CaracteristicaEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface CaracteristicaR2dbcRepository extends ReactiveCrudRepository<CaracteristicaEntity, Long> {

    Mono<CaracteristicaEntity> findByCodigo(String codigo);
}
