package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import com.gymadmin.platform.domain.model.PagoPendienteValidacion;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PagoPendienteResponse(
        Long id,
        Long idCompania,
        String nombreCompania,
        Long idPlanDestino,
        BigDecimal monto,
        String moneda,
        Instant fechaReporte,
        LocalDate fechaTransferencia,
        String comprobanteUrl,
        String bancoOrigen,
        String referencia,
        String estado,
        String motivoRechazo,
        Long aprobadoPor,
        Instant fechaAprobacion,
        boolean activacionProgramada
) {
    /**
     * Overload legacy sin nombre de compañía. Los llamadores nuevos deberían usar
     * {@link #from(PagoPendienteValidacion, String)} para que el frontend pueda
     * mostrar la etiqueta de la compañía sin resolver el ID.
     */
    public static PagoPendienteResponse from(PagoPendienteValidacion p) {
        return from(p, null);
    }

    public static PagoPendienteResponse from(PagoPendienteValidacion p, String nombreCompania) {
        return new PagoPendienteResponse(
                p.getId(),
                p.getIdCompania(),
                nombreCompania,
                p.getIdPlanDestino(),
                p.getMonto(),
                p.getMoneda(),
                p.getFechaReporte(),
                p.getFechaTransferencia(),
                p.getComprobanteUrl(),
                p.getBancoOrigen(),
                p.getReferencia(),
                p.getEstado() != null ? p.getEstado().name() : null,
                p.getMotivoRechazo(),
                p.getAprobadoPor(),
                p.getFechaAprobacion(),
                p.isActivacionProgramada()
        );
    }
}
