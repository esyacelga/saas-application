package com.gymadmin.billing.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.Size;

/**
 * Request body opcional para {@code POST /api/v1/anulaciones/{id}/aprobar} y
 * {@code POST /api/v1/anulaciones/{id}/confirmar-sri}. La observación es
 * opcional (informativa).
 */
public record AprobarAnulacionRequest(
        @Size(max = 500, message = "Máximo 500 caracteres")
        String observacion
) {
}
