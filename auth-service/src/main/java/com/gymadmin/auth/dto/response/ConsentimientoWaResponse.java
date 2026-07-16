package com.gymadmin.auth.dto.response;

import java.time.OffsetDateTime;

/**
 * Fase 6 (bloque E): estado del opt-in de WhatsApp del socio tras actualizarlo.
 * {@code fechaConsentimientoWa} es {@code null} cuando {@code aceptaWhatsapp=false}.
 */
public record ConsentimientoWaResponse(
        Integer idPersona,
        boolean aceptaWhatsapp,
        OffsetDateTime fechaConsentimientoWa
) {}
