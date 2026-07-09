package com.gymadmin.billing.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.billing.infrastructure.adapter.out.persistence.entity.CertificadoEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CertificadoR2dbcRepository extends ReactiveCrudRepository<CertificadoEntity, Long> {

    @Query("""
            SELECT * FROM facturacion.certificados
            WHERE id_compania = :idCompania
              AND id_sucursal = :idSucursal
              AND activo = true
              AND fecha_vencimiento > CURRENT_DATE
            LIMIT 1
            """)
    Mono<CertificadoEntity> findActiveByEmpresa(Integer idCompania, Integer idSucursal);

    @Query("""
            SELECT * FROM facturacion.certificados
            WHERE activo = true
              AND fecha_vencimiento <= CURRENT_DATE + (:dias * INTERVAL '1 day')
              AND fecha_vencimiento > CURRENT_DATE
            """)
    Flux<CertificadoEntity> findProximosAVencer(int dias);
}
