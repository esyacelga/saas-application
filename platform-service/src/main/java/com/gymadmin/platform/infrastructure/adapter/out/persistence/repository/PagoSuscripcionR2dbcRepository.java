package com.gymadmin.platform.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.PagoSuscripcionEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface PagoSuscripcionR2dbcRepository extends ReactiveCrudRepository<PagoSuscripcionEntity, Long> {

    @Query("SELECT p.* FROM tenant.pagos_suscripcion p " +
           "JOIN tenant.compania_planes cp ON p.id_compania_plan = cp.id " +
           "WHERE cp.id_compania = :idCompania ORDER BY p.fecha_pago DESC")
    Flux<PagoSuscripcionEntity> findByIdCompania(Long idCompania);
}
