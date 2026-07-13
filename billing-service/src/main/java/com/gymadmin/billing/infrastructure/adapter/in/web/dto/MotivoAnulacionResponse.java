package com.gymadmin.billing.infrastructure.adapter.in.web.dto;

import com.gymadmin.billing.domain.model.sri.MotivoAnulacionNcSri;

/**
 * Vista pública del catálogo {@code sri.motivos_anulacion_nc}.
 */
public record MotivoAnulacionResponse(
        Integer id,
        String codigo,
        String descripcion
) {
    public static MotivoAnulacionResponse from(MotivoAnulacionNcSri motivo) {
        return new MotivoAnulacionResponse(motivo.id(), motivo.codigo(), motivo.descripcion());
    }
}
