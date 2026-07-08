package com.gymadmin.attendance.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.attendance.infrastructure.adapter.out.persistence.entity.PlantillaMensajeEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PlantillaMensajeR2dbcRepository extends ReactiveCrudRepository<PlantillaMensajeEntity, Integer> {

    @Query("SELECT * FROM asistencia.plantillas_mensajes WHERE id_compania = :idCompania AND eliminado = false ORDER BY tipo, nombre")
    Flux<PlantillaMensajeEntity> findByCompania(Integer idCompania);

    @Query("""
            SELECT COUNT(*) FROM asistencia.plantillas_mensajes
            WHERE id_compania = :idCompania AND tipo = :tipo AND activo = true AND eliminado = false
            """)
    Mono<Long> countActivasByTipo(Integer idCompania, String tipo);

    @Query("""
            SELECT * FROM asistencia.plantillas_mensajes
            WHERE id_compania = :idCompania AND tipo = :tipo AND activo = true AND eliminado = false
            ORDER BY RANDOM() LIMIT 1
            """)
    Mono<PlantillaMensajeEntity> findRandomActivaByTipo(Integer idCompania, String tipo);
}
