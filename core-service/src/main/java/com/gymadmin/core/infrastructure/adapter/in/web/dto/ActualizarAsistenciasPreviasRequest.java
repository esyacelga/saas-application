package com.gymadmin.core.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ActualizarAsistenciasPreviasRequest(
        @NotNull @Min(0) Integer cantidad
) {}
