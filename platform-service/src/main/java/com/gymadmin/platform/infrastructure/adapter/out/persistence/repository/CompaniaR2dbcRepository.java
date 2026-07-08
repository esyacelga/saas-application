package com.gymadmin.platform.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.CompaniaEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface CompaniaR2dbcRepository extends ReactiveCrudRepository<CompaniaEntity, Long> {

    Mono<CompaniaEntity> findByRuc(String ruc);
}
