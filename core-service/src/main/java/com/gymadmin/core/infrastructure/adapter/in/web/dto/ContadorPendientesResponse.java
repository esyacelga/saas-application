package com.gymadmin.core.infrastructure.adapter.in.web.dto;

import com.gymadmin.core.domain.port.in.MembresiaUseCase.ContadorPendientesResult;

/**
 * Payload del badge del dashboard staff: total + desglose por origen. La UI usa
 * {@code por_origen.cliente} para llamar la atención sobre las solicitudes autoservicio
 * pendientes de confirmar.
 */
public record ContadorPendientesResponse(
        long total,
        PorOrigen porOrigen
) {
    public record PorOrigen(long cliente, long staff) {}

    public static ContadorPendientesResponse from(ContadorPendientesResult r) {
        return new ContadorPendientesResponse(
                r.total(),
                new PorOrigen(r.porOrigenCliente(), r.porOrigenStaff())
        );
    }
}
