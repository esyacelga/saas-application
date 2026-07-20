package com.gymadmin.core.infrastructure.adapter.in.web.dto;

import com.gymadmin.core.domain.model.MetodoPago;

public record MetodoPagoResponse(
        Long id,
        String nombre,
        Boolean activo
) {
    public static MetodoPagoResponse from(MetodoPago m) {
        return new MetodoPagoResponse(m.getId(), m.getNombre(), m.getActivo());
    }
}
