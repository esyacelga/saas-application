package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import java.time.Instant;

/**
 * Fase 6 (bloque E): estado del opt-in de WhatsApp del dueño tras actualizarlo.
 * {@code fechaConsentimientoWa} es {@code null} cuando {@code aceptaWhatsapp=false}.
 */
public record ConsentimientoWaResponse(
        Long idCompania,
        boolean aceptaWhatsapp,
        Instant fechaConsentimientoWa
) {}
