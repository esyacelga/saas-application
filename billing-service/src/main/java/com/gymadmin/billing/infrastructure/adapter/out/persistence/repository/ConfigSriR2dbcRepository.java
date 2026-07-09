package com.gymadmin.billing.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.billing.infrastructure.adapter.out.persistence.entity.ConfigSriEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface ConfigSriR2dbcRepository extends ReactiveCrudRepository<ConfigSriEntity, Long> {

    @Query("""
            SELECT * FROM facturacion.config_sri
            WHERE id_compania = :idCompania
              AND id_sucursal = :idSucursal
              AND activo = true
            LIMIT 1
            """)
    Mono<ConfigSriEntity> findActiveByEmpresa(Integer idCompania, Integer idSucursal);
}
