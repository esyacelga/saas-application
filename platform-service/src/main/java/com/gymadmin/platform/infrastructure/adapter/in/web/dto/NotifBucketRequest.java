package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Fase 6 (R1): body para actualizar el bucket de aviso previo de un destinatario.
 * El día 0 no se toca aquí (es fijo en el código del job).
 */
public record NotifBucketRequest(
        @NotNull(message = "diasPrevio is required")
        @Min(value = 1, message = "diasPrevio debe ser >= 1")
        @Max(value = 30, message = "diasPrevio debe ser <= 30")
        Integer diasPrevio,

        @NotNull(message = "activo is required")
        Boolean activo
) {}
