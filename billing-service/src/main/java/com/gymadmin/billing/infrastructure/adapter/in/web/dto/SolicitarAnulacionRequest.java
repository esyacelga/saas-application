package com.gymadmin.billing.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body para {@code POST /api/v1/comprobantes/{id}/anular}.
 * <p>
 * El {@code idCompania} sale del JWT (nunca del body). El {@code codigo_motivo_anulacion}
 * es opcional para Flujo A pero obligatorio para Flujo B — si se pide NC sin
 * motivo válido, la aprobación fallará con 422.
 */
public record SolicitarAnulacionRequest(
        @NotBlank
        @Size(min = 5, max = 500, message = "Debe tener entre 5 y 500 caracteres")
        String motivo,
        /** Código funcional del catálogo {@code sri.motivos_anulacion_nc}. Opcional para Flujo A. */
        String codigoMotivoAnulacion,
        /** {@code true} para Flujo B (NC), {@code false} para Flujo A. Default {@code false}. */
        Boolean generarNotaCredito
) {
    public boolean generarNotaCreditoOrDefault() {
        return Boolean.TRUE.equals(generarNotaCredito);
    }
}
