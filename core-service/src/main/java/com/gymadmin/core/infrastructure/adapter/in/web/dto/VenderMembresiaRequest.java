package com.gymadmin.core.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VenderMembresiaRequest(
        @NotNull Long idTipoMembresia,
        @NotNull LocalDate fechaInicio,
        Long idMetodoPago,
        BigDecimal descuentoAplicado
) {}
