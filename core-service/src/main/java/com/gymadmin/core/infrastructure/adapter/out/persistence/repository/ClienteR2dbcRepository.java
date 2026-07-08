package com.gymadmin.core.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.core.infrastructure.adapter.out.persistence.entity.ClienteEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ClienteR2dbcRepository extends ReactiveCrudRepository<ClienteEntity, Long> {

    @Query("""
        SELECT * FROM core.clientes
        WHERE id_compania = :idCompania
          AND eliminado = false
          AND (:estado IS NULL OR estado = :estado)
          AND (:buscar IS NULL OR lower(codigo_carnet) LIKE lower(concat('%', :buscar, '%')))
        ORDER BY id DESC
        LIMIT :limit OFFSET :offset
        """)
    Flux<ClienteEntity> findByIdCompania(Long idCompania, String estado, String buscar, int offset, int limit);

    @Query("""
        SELECT COUNT(*) FROM core.clientes
        WHERE id_compania = :idCompania
          AND eliminado = false
          AND (:estado IS NULL OR estado = :estado)
          AND (:buscar IS NULL OR lower(codigo_carnet) LIKE lower(concat('%', :buscar, '%')))
        """)
    Mono<Long> countByIdCompania(Long idCompania, String estado, String buscar);

    @Query("SELECT * FROM core.clientes WHERE id = :id AND id_compania = :idCompania AND eliminado = false")
    Mono<ClienteEntity> findByIdAndIdCompania(Long id, Long idCompania);

    @Query("SELECT * FROM core.clientes WHERE id_persona = :idPersona AND id_compania = :idCompania AND eliminado = false")
    Mono<ClienteEntity> findByIdPersonaAndIdCompania(Long idPersona, Long idCompania);

    @Query("SELECT * FROM core.clientes WHERE id_persona = :idPersona AND eliminado = false ORDER BY id DESC")
    Flux<ClienteEntity> findByIdPersona(Long idPersona);

    @Query("SELECT * FROM core.clientes WHERE estado IN ('activo','proximo_vencer','vencido','riesgo_abandono') AND eliminado = false")
    Flux<ClienteEntity> findActivosParaJob();
}
