package com.gymadmin.platform.domain.port.in;

import com.gymadmin.platform.domain.model.ModuloCheckResult;
import reactor.core.publisher.Mono;

public interface ModuloCheckUseCase {

    Mono<ModuloCheckResult> checkAcceso(Long idCompania, String codigo);

    /**
     * REQ-SAAS-001 — invalida el cache Redis de módulos permitidos para el tenant
     * dado. Se llama tras cualquier cambio en su suscripción activa (activar Trial,
     * aprobar pago, cancelar, degradar automático). Retorna la cantidad de keys
     * borradas.
     */
    Mono<Long> invalidateCacheByCompania(Long idCompania);
}
