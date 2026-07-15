package com.gymadmin.platform.domain.port.in;

import reactor.core.publisher.Mono;

/**
 * REQ-SAAS-001 (Fase 3): puerto in del worker que procesa la cola de notificaciones
 * por WhatsApp del dueño ({@code canal='whatsapp'} en
 * {@code tenant.notificaciones_suscripcion}).
 *
 * <p>Hermano de {@link ProcesarColaEmailsUseCase} (C1): comparte la tabla y el
 * mecanismo de claim/backoff, pero con su propio sender ({@code WhatsAppSender}) y
 * su propia regla cross-day para el aviso del día 0.
 */
public interface ProcesarColaWhatsAppUseCase {

    /** Procesa hasta {@code max} notificaciones WhatsApp pendientes; retorna cuántas se procesaron. */
    Mono<Integer> procesarLote(int max);
}
