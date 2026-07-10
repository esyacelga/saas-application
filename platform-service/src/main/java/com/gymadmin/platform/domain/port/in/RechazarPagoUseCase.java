package com.gymadmin.platform.domain.port.in;

import reactor.core.publisher.Mono;

/**
 * REQ-SAAS-001 (RN-08): un operador root rechaza un pago pendiente. El motivo
 * es obligatorio (mínimo 10 chars).
 */
public interface RechazarPagoUseCase {

    Mono<Void> rechazar(Long idPagoPendiente, Long idUsuarioRoot, String motivo);
}
