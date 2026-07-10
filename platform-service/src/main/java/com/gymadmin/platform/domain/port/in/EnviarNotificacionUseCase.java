package com.gymadmin.platform.domain.port.in;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * REQ-SAAS-001 (Sub-fase 1.5): puerto in para encolar una notificación (email o
 * banner) en {@code tenant.notificaciones_suscripcion}.
 */
public interface EnviarNotificacionUseCase {

    Mono<Long> encolar(EncolarNotificacionCommand cmd);

    record EncolarNotificacionCommand(
            Long idCompania,
            Long idCompaniaPlan,
            String tipo,
            Integer diasAntes,
            String canal,
            String templateKey,
            Map<String, Object> variables,
            String destinatario
    ) {}
}
