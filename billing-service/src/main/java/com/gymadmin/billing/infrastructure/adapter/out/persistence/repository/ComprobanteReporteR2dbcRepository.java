package com.gymadmin.billing.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.billing.infrastructure.adapter.out.persistence.entity.ComprobanteEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface ComprobanteReporteR2dbcRepository extends ReactiveCrudRepository<ComprobanteEntity, Long> {

    @Query("""
            SELECT * FROM facturacion.comprobantes
            WHERE id_compania = :idCompania
              AND estado = 'AUTORIZADO'
              AND EXTRACT(YEAR FROM fecha_emision) = :anio
              AND EXTRACT(MONTH FROM fecha_emision) = :mes
            ORDER BY fecha_emision ASC
            """)
    Flux<ComprobanteEntity> findAutorizadosPorMes(Integer idCompania, Integer anio, Integer mes);

    /**
     * G9 · Anulados del mes, para el nodo {@code anulados} del ATS. El SRI los
     * reporta aparte de las ventas, no como una venta en negativo — y como la
     * anulación mueve el comprobante a estado {@code ANULADO}, la query de
     * autorizados ya los excluye por construcción.
     */
    @Query("""
            SELECT * FROM facturacion.comprobantes
            WHERE id_compania = :idCompania
              AND estado = 'ANULADO'
              AND EXTRACT(YEAR FROM fecha_emision) = :anio
              AND EXTRACT(MONTH FROM fecha_emision) = :mes
            ORDER BY cod_establecimiento, cod_punto_emision, secuencial
            """)
    Flux<ComprobanteEntity> findAnuladosPorMes(Integer idCompania, Integer anio, Integer mes);
}
