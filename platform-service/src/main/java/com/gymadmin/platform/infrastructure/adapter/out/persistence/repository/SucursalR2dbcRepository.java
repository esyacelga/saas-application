package com.gymadmin.platform.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.SucursalEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SucursalR2dbcRepository extends ReactiveCrudRepository<SucursalEntity, Long> {

    Flux<SucursalEntity> findByIdCompania(Long idCompania);

    Mono<SucursalEntity> findByQrToken(String qrToken);
}
