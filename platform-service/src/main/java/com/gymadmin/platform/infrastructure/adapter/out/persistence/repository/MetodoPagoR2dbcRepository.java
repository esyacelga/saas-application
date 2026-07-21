package com.gymadmin.platform.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.MetodoPagoEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface MetodoPagoR2dbcRepository extends ReactiveCrudRepository<MetodoPagoEntity, Long> {

    Flux<MetodoPagoEntity> findByIdCompaniaAndEliminadoFalse(Long idCompania);
}
