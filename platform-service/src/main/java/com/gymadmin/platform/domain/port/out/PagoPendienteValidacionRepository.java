package com.gymadmin.platform.domain.port.out;

import com.gymadmin.platform.domain.model.PagoPendienteValidacion;
import reactor.core.publisher.Mono;

/**
 * REQ-SAAS-001 (RN-08): puerto out para el buzón de pagos pendientes de validación.
 * <p>
 * Sub-fase 1.2 solo requiere lo mínimo (persistir, leer por id y por hash de
 * idempotencia). La Sub-fase 1.3 (aprobación/rechazo por root) añadirá listados
 * paginados y transiciones de estado.
 */
public interface PagoPendienteValidacionRepository {

    Mono<PagoPendienteValidacion> save(PagoPendienteValidacion pago);

    Mono<PagoPendienteValidacion> findById(Long id);

    /**
     * Devuelve el pago pendiente/aprobado con ese hash (invariante RN-08).
     * Emite {@code Mono.empty()} si no existe uno vigente.
     */
    Mono<PagoPendienteValidacion> findByHashIdempotencia(String hash);
}
