package com.gymadmin.platform.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.PersonaEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface PersonaR2dbcRepository extends ReactiveCrudRepository<PersonaEntity, Long> {

    Mono<PersonaEntity> findByCi(String ci);
}
