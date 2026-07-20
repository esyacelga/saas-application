package com.gymadmin.core.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.core.infrastructure.adapter.out.persistence.entity.MetodoPagoEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface MetodoPagoR2dbcRepository extends ReactiveCrudRepository<MetodoPagoEntity, Long> {

    @Query("SELECT * FROM config.metodos_pago WHERE id_compania = :idCompania AND activo = true AND eliminado = false ORDER BY nombre")
    Flux<MetodoPagoEntity> findByIdCompaniaAndActivoTrueAndEliminadoFalse(Long idCompania);
}
