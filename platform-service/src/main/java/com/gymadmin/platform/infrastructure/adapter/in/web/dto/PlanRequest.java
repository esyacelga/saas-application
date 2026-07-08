package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PlanRequest(
        @NotBlank(message = "nombre is required")
        String nombre,

        String descripcion,

        @NotNull(message = "precioMensual is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "precioMensual must be greater than 0")
        BigDecimal precioMensual
) {}
