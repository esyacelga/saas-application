package com.gymadmin.billing.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body para {@code POST /api/v1/anulaciones/{id}/rechazar}. La
 * observación es obligatoria (auditoría interna).
 */
public record RechazarAnulacionRequest(
        @NotBlank
        @Size(min = 5, max = 500, message = "Debe tener entre 5 y 500 caracteres")
        String observacion
) {
}
