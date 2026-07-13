package com.gymadmin.billing.domain.port.out;

import com.gymadmin.billing.domain.model.NotaCreditoReferencia;
import reactor.core.publisher.Mono;

/**
 * Puerto persistente para la tabla
 * {@code facturacion.notas_credito_referencias}. Cada NC tiene, a lo sumo, una
 * fila (constraint {@code UNIQUE (id_comprobante)}).
 */
public interface NotaCreditoReferenciaRepository {

    /**
     * Inserta la referencia. El caller debe haber persistido el comprobante NC
     * previamente para tener {@code idComprobante}.
     */
    Mono<NotaCreditoReferencia> save(NotaCreditoReferencia referencia);

    /**
     * Busca la referencia por {@code idComprobante} (el ID de la NC, no de la
     * factura original). Devuelve {@link Mono#empty()} si no existe.
     */
    Mono<NotaCreditoReferencia> findByIdComprobante(Long idComprobante);
}
