package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import java.time.LocalDate;

public record CompaniaResponse(
        Long id,
        String nombre,
        String ruc,
        String telefono,
        String whatsapp,
        String correo,
        String logoUrl,
        Boolean activo,
        PlanActivoDto planActivo
) {
    public record PlanActivoDto(
            String nombre,
            String estado,
            LocalDate fechaFin,
            Long diasRestantes
    ) {}
}
