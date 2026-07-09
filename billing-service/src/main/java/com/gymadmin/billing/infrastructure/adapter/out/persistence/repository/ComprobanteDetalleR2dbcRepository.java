package com.gymadmin.billing.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.billing.infrastructure.adapter.out.persistence.entity.ComprobanteDetalleEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface ComprobanteDetalleR2dbcRepository extends ReactiveCrudRepository<ComprobanteDetalleEntity, Long> {

    @Query("""
            SELECT * FROM facturacion.comprobantes_detalle
            WHERE id_comprobante = :idComprobante
            ORDER BY orden ASC
            """)
    Flux<ComprobanteDetalleEntity> findByIdComprobante(Long idComprobante);
}
