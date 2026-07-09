package com.gymadmin.platform.domain.port.in;

import com.gymadmin.platform.domain.model.CompaniaPlan;
import reactor.core.publisher.Mono;

/**
 * REQ-SAAS-001 (RN-01): activa el Trial único por tenant.
 */
public interface ActivarTrialUseCase {

    Mono<CompaniaPlan> activar(Long idCompania, Long idUsuarioActor);
}
