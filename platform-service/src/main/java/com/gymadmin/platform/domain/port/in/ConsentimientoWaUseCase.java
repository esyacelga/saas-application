package com.gymadmin.platform.domain.port.in;

import com.gymadmin.platform.domain.model.Compania;
import reactor.core.publisher.Mono;

/**
 * Fase 6 (bloque E): captura del opt-in de WhatsApp del dueño de una compañía.
 * {@code acepta_whatsapp} + {@code fecha_consentimiento_wa} (sella cuándo, prueba mínima ante Meta).
 */
public interface ConsentimientoWaUseCase {

    /**
     * Registra el opt-in/opt-out del dueño. Cuando {@code acepta=true} sella la fecha con el reloj;
     * cuando {@code acepta=false} (opt-out) la limpia a {@code null}.
     */
    Mono<Compania> actualizarConsentimiento(Long idCompania, boolean acepta);
}
