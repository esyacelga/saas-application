package com.gymadmin.billing.domain.port.out;

import reactor.core.publisher.Mono;

/**
 * Reserva atómica del siguiente número secuencial para un punto de emisión
 * y tipo de comprobante. La implementación debe garantizar consistencia bajo
 * concurrencia (dos requests simultáneos deben obtener secuenciales distintos
 * y consecutivos).
 */
public interface SecuencialRepository {

    /**
     * Reserva y retorna el siguiente secuencial disponible para la combinación
     * indicada. El caller es responsable de formatear el entero a 9 dígitos
     * con padding a la izquierda para el XML SRI.
     *
     * @param idCompania         empresa dueña del comprobante
     * @param idSucursal         sucursal donde se emite
     * @param codEstablecimiento código SRI del establecimiento (3 dígitos)
     * @param codPuntoEmision    código SRI del punto de emisión (3 dígitos)
     * @param tipoComprobante    tipo de comprobante SRI (ej. "01" factura, "04" NC)
     * @return el próximo secuencial reservado (1, 2, 3...)
     */
    Mono<Integer> reservarSiguiente(Integer idCompania,
                                    Integer idSucursal,
                                    String codEstablecimiento,
                                    String codPuntoEmision,
                                    String tipoComprobante);
}
