package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Fase 6 (bloque E): body para el opt-in/opt-out de WhatsApp del dueño.
 */
public record ConsentimientoWaRequest(
        @NotNull(message = "acepta is required")
        Boolean acepta
) {}
