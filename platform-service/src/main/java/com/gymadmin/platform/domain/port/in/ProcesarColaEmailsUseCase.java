package com.gymadmin.platform.domain.port.in;

import reactor.core.publisher.Mono;

/**
 * REQ-SAAS-001 (Sub-fase 1.5): puerto in para procesar el lote de notificaciones
 * pendientes (canal=email) con FOR UPDATE SKIP LOCKED.
 */
public interface ProcesarColaEmailsUseCase {

    Mono<Integer> procesarLote(int max);
}
