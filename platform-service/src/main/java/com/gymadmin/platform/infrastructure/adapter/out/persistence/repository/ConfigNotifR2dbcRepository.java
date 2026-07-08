package com.gymadmin.platform.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.ConfigNotifSuscripcionEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ConfigNotifR2dbcRepository extends ReactiveCrudRepository<ConfigNotifSuscripcionEntity, Long> {

    Flux<ConfigNotifSuscripcionEntity> findByIdCompania(Long idCompania);

    Mono<Void> deleteByIdCompania(Long idCompania);
}
