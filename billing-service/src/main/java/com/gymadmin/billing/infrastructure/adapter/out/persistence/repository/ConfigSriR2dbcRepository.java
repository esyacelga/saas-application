package com.gymadmin.billing.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.billing.infrastructure.adapter.out.persistence.entity.ConfigSriEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.Repository;
import reactor.core.publisher.Mono;

/**
 * facturacion.config_sri uses a composite PK (id_compania, id_sucursal); ReactiveCrudRepository
 * cannot handle composite keys cleanly, so this interface only declares query methods.
 * Inserts / updates must go through DatabaseClient in ConfigSriPersistenceAdapter when needed.
 */
public interface ConfigSriR2dbcRepository extends Repository<ConfigSriEntity, Void> {

    @Query("""
            SELECT * FROM facturacion.config_sri
            WHERE id_compania = :idCompania
              AND id_sucursal = :idSucursal
              AND facturacion_activa = true
            LIMIT 1
            """)
    Mono<ConfigSriEntity> findActiveByEmpresa(Integer idCompania, Integer idSucursal);

    @Query("""
            SELECT * FROM facturacion.config_sri
            WHERE id_compania = :idCompania
              AND facturacion_activa = true
            ORDER BY id_sucursal ASC
            LIMIT 1
            """)
    Mono<ConfigSriEntity> findFirstActiveByCompania(Integer idCompania);
}
