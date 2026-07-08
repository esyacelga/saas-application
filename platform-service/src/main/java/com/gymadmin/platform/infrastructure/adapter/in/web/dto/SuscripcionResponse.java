package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import java.time.LocalDate;

public record SuscripcionResponse(
        Long id,
        Long idPlan,
        String estado,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        Long diasRestantes,
        Integer diasGracia,
        String tipoCambio
) {}
