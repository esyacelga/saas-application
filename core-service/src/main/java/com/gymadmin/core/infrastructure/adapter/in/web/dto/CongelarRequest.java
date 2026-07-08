package com.gymadmin.core.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CongelarRequest(
        @NotNull LocalDate fechaInicio,
        @NotNull String motivo,
        String detalle,
        boolean retroactivo,
        String documentoRespaldo,
        Long aprobadoPor
) {}
