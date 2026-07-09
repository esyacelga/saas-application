package com.gymadmin.billing.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DetalleItem(
        @NotBlank String codigoPrincipal,
        String codigoAuxiliar,
        @NotBlank String descripcion,
        @NotNull @DecimalMin("0.000001") BigDecimal cantidad,
        @NotNull @DecimalMin("0.00") BigDecimal precioUnitario,
        BigDecimal descuento
) {}
