package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import com.gymadmin.platform.domain.model.PagoPendienteValidacion;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record PagoPendienteResponse(
        Long id,
        Long idCompania,
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
    public static PagoPendienteResponse from(PagoPendienteValidacion p) {
        return new PagoPendienteResponse(
                p.getId(),
                p.getIdCompania(),
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
