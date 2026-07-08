package com.gymadmin.core.infrastructure.adapter.in.web.dto;

import com.gymadmin.core.domain.model.TipoMembresia;

import java.math.BigDecimal;

public record TipoMembresiaResponse(
        Long id,
        String nombre,
        String modoControl,
        String duracionTipo,
        Integer duracionValor,
        Integer diasAcceso,
        BigDecimal precio,
        Boolean activo
) {
    public static TipoMembresiaResponse from(TipoMembresia t) {
        return new TipoMembresiaResponse(
                t.getId(), t.getNombre(),
                t.getModoControl() != null ? t.getModoControl().name() : null,
                t.getDuracionTipo() != null ? t.getDuracionTipo().name() : null,
                t.getDuracionValor(), t.getDiasAcceso(), t.getPrecio(), t.getActivo()
        );
    }
}
