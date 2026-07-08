package com.gymadmin.core.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.core.infrastructure.adapter.out.persistence.entity.CongelamientoEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CongelamientoR2dbcRepository extends ReactiveCrudRepository<CongelamientoEntity, Long> {

    @Query("SELECT * FROM core.congelamientos WHERE id_membresia = :idMembresia ORDER BY creacion_fecha DESC")
    Flux<CongelamientoEntity> findByIdMembresia(Long idMembresia);

    @Query("SELECT * FROM core.congelamientos WHERE id_membresia = :idMembresia AND fecha_fin IS NULL LIMIT 1")
    Mono<CongelamientoEntity> findActivoByIdMembresia(Long idMembresia);
}
