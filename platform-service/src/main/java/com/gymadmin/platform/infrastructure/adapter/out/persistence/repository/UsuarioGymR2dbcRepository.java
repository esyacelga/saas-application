package com.gymadmin.platform.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.UsuarioGymEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface UsuarioGymR2dbcRepository extends ReactiveCrudRepository<UsuarioGymEntity, Long> {

    Mono<UsuarioGymEntity> findByIdCompaniaAndCorreoAndEliminadoFalse(Long idCompania, String correo);
}
