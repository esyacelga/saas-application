package com.gymadmin.platform.domain.port.out;

import reactor.core.publisher.Mono;

public interface MetodoPagoRepository {

    /**
     * Crea los métodos de pago por defecto (Efectivo, Tarjeta, Transferencia) para la
     * compañía/sucursal recién registrada, si aún no existen. Idempotente por nombre.
     */
    Mono<Void> crearPorDefecto(Long idCompania, Long idSucursal);
}
