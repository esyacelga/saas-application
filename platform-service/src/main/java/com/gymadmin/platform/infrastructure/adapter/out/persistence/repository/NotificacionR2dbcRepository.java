package com.gymadmin.platform.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.NotificacionSuscripcionEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface NotificacionR2dbcRepository extends ReactiveCrudRepository<NotificacionSuscripcionEntity, Long> {

    Flux<NotificacionSuscripcionEntity> findByIdCompaniaPlan(Long idCompaniaPlan);

    Mono<Boolean> existsByIdCompaniaPlanAndDiasAntes(Long idCompaniaPlan, Integer diasAntes);
}
