package com.gymadmin.core.infrastructure.adapter.in.web.dto;

import com.gymadmin.core.domain.model.Membresia;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MembresiaResponse(
        Long id,
        Long idCliente,
        Long idTipoMembresia,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        Integer diasAccesoTotal,
        BigDecimal precioPagado,
        BigDecimal descuentoAplicado,
        String estado,
        String estadoPago,
        Boolean eliminado,
        String motivoEliminacion
) {
    public static MembresiaResponse from(Membresia m) {
        return new MembresiaResponse(
                m.getId(), m.getIdCliente(), m.getIdTipoMembresia(),
                m.getFechaInicio(), m.getFechaFin(), m.getDiasAccesoTotal(),
                m.getPrecioPagado(), m.getDescuentoAplicado(),
                m.getEstado() != null ? m.getEstado().name() : null,
                m.getEstadoPago() != null ? m.getEstadoPago().name() : null,
                m.getEliminado(),
                m.getMotivoEliminacion() != null ? m.getMotivoEliminacion().name() : null
        );
    }
}
