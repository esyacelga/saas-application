package com.gymadmin.billing.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.billing.infrastructure.adapter.out.persistence.entity.EnvioSriEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface EnvioSriR2dbcRepository extends ReactiveCrudRepository<EnvioSriEntity, Long> {

    Flux<EnvioSriEntity> findByIdComprobante(Long idComprobante);
}
