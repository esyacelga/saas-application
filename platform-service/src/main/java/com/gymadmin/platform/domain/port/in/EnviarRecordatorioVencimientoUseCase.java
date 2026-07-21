package com.gymadmin.platform.domain.port.in;

import reactor.core.publisher.Mono;

/**
 * GYM-002: disparo manual, directo e inmediato, de un recordatorio de vencimiento de suscripción
 * por WhatsApp al dueño de una compañía (botón del panel de plataforma).
 *
 * <p>NO encola: envía y devuelve el resultado real (éxito/fallo). Reglas mínimas: opt-in de
 * WhatsApp + teléfono normalizable a E.164 + suscripción activa. Permite cualquier plan, reenvíos,
 * día 0 y días vencidos (negativos).
 */
public interface EnviarRecordatorioVencimientoUseCase {

    Mono<Resultado> enviar(Long idCompania);

    /**
     * @param enviado  siempre {@code true} cuando el {@link Mono} completa (los fallos se propagan
     *                 como excepción con {@code codigo}); presente para un body de respuesta explícito.
     * @param telefono teléfono E.164 al que se envió (p. ej. {@code +593987654321}).
     * @param template plantilla HSM usada según los días restantes.
     */
    record Resultado(boolean enviado, String telefono, String template) {
    }
}
