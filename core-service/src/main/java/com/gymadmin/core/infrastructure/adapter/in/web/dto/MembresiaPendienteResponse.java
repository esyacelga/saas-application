package com.gymadmin.core.infrastructure.adapter.in.web.dto;

import com.gymadmin.core.domain.port.in.MembresiaUseCase.MembresiaPendienteResult;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Fila del dashboard "Ventas pendientes" (§4.8). Refleja una membresía en
 * {@code estado_pago='PENDIENTE'} y {@code eliminado=false}. El frontend calcula
 * el "hace X días" a partir de {@code creacionFecha}.
 */
public record MembresiaPendienteResponse(
        Long id,
        Long idCliente,
        String nombreCliente,
        Long idTipoMembresia,
        String tipoNombre,
        String modoControl,
        BigDecimal precioPagado,
        BigDecimal descuentoAplicado,
        OffsetDateTime creacionFecha
) {
    public static MembresiaPendienteResponse from(MembresiaPendienteResult r) {
        return new MembresiaPendienteResponse(
                r.membresia().getId(),
                r.membresia().getIdCliente(),
                r.nombreCliente(),
                r.membresia().getIdTipoMembresia(),
                r.tipoNombre(),
                r.modoControl(),
                r.membresia().getPrecioPagado(),
                r.membresia().getDescuentoAplicado(),
                r.membresia().getCreatedAt()
        );
    }
}
