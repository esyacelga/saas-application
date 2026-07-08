package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RegistrarPagoRequest(
        @NotNull(message = "idCompaniaPlan is required")
        Long idCompaniaPlan,

        @NotNull(message = "monto is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "monto must be greater than 0")
        BigDecimal monto,

        @NotBlank(message = "metodoPago is required")
        String metodoPago,

        @NotBlank(message = "tipoPago is required")
        String tipoPago,

        String referencia,
        LocalDate periodoDesde,
        LocalDate periodoHasta
) {}
