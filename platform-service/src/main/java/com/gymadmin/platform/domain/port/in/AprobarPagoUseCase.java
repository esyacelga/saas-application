package com.gymadmin.platform.domain.port.in;

import com.gymadmin.platform.domain.model.CompaniaPlan;
import reactor.core.publisher.Mono;

/**
 * REQ-SAAS-001 (RN-08): un operador root aprueba un pago pendiente. La
 * aprobación crea (o programa) la suscripción Premium correspondiente.
 */
public interface AprobarPagoUseCase {

    Mono<CompaniaPlan> aprobar(Long idPagoPendiente, Long idUsuarioRoot);
}
