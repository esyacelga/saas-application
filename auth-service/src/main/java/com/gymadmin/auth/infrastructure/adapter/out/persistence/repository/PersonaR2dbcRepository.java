package com.gymadmin.auth.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.PersonaEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PersonaR2dbcRepository extends ReactiveCrudRepository<PersonaEntity, Integer> {

    Mono<PersonaEntity> findByCi(String ci);
    Mono<Boolean> existsByCi(String ci);
    Mono<Boolean> existsByCiAndIdNot(String ci, Integer id);
    Mono<PersonaEntity> findByCorreo(String correo);

    @Query("""
            SELECT * FROM identidad.personas
            WHERE (:nombre IS NULL OR lower(nombre) LIKE lower(concat('%', :nombre, '%')))
              AND (:ci     IS NULL OR ci LIKE concat('%', :ci, '%'))
              AND (:correo IS NULL OR lower(correo) LIKE lower(concat('%', :correo, '%')))
              AND (:sexo   IS NULL OR sexo = :sexo)
            ORDER BY nombre ASC
            LIMIT :limit OFFSET :offset
            """)
    Flux<PersonaEntity> findAllFiltered(String nombre, String ci, String correo, String sexo, int limit, int offset);

    @Query("""
            SELECT COUNT(*) FROM identidad.personas
            WHERE (:nombre IS NULL OR lower(nombre) LIKE lower(concat('%', :nombre, '%')))
              AND (:ci     IS NULL OR ci LIKE concat('%', :ci, '%'))
              AND (:correo IS NULL OR lower(correo) LIKE lower(concat('%', :correo, '%')))
              AND (:sexo   IS NULL OR sexo = :sexo)
            """)
    Mono<Long> countAllFiltered(String nombre, String ci, String correo, String sexo);
}
