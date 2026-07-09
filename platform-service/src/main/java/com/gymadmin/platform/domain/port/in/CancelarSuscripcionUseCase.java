package com.gymadmin.platform.domain.port.in;

import reactor.core.publisher.Mono;

/**
 * REQ-SAAS-001 (RN-09): cancelación voluntaria del owner.
 */
public interface CancelarSuscripcionUseCase {

    Mono<Void> cancelar(Long idCompania, Long idUsuarioActor, String motivoOpcional);
}
