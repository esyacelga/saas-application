package com.gymadmin.auth.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.UsuarioAppEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UsuarioAppR2dbcRepository extends ReactiveCrudRepository<UsuarioAppEntity, Integer> {
    Mono<UsuarioAppEntity> findByLoginAndIdCompania(String login, Integer idCompania);
    Mono<Boolean> existsByIdPersonaAndIdCompania(Integer idPersona, Integer idCompania);
    Mono<UsuarioAppEntity> findByTokenRecuperacion(String token);
    Flux<UsuarioAppEntity> findByIdPersona(Integer idPersona);
}
