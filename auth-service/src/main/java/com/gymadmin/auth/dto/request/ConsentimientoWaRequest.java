package com.gymadmin.auth.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Fase 6 (bloque E): body para el opt-in/opt-out de WhatsApp del socio.
 */
public record ConsentimientoWaRequest(
        @NotNull(message = "acepta is required")
        Boolean acepta
) {}
