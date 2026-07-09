package com.gymadmin.billing.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PagoItem(
        @NotBlank String formaPago,
        @NotNull @DecimalMin("0.00") BigDecimal total,
        Integer plazo,
        String unidadTiempo
) {}
