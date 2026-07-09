package com.gymadmin.platform.domain.port.in;

import com.gymadmin.platform.domain.model.RecursoLimitable;
import reactor.core.publisher.Mono;

/**
 * REQ-SAAS-001 (RN-05): puerta de entrada para validar cuotas antes de crear
 * cualquier recurso limitable (sucursales, clientes activos, staff).
 */
public interface LimiteRecursoUseCase {

    /**
     * Valida que el tenant pueda crear un nuevo recurso del tipo indicado bajo
     * su plan activo. Adquiere {@code pg_advisory_xact_lock(idCompania)} para
     * serializar accesos concurrentes del mismo tenant.
     *
     * @throws com.gymadmin.platform.domain.exception.LimiteAlcanzadoException si el uso actual iguala o supera el máximo del plan.
     */
    Mono<Void> validarPuedeCrear(Long idCompania, RecursoLimitable recurso);
}
