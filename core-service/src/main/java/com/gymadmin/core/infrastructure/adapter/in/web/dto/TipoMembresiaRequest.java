package com.gymadmin.core.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TipoMembresiaRequest(
        @NotBlank String nombre,
        @NotNull String modoControl,
        @NotNull String duracionTipo,
        @NotNull @Positive Integer duracionValor,
        Integer diasAcceso,
        @NotNull @Positive BigDecimal precio
) {}
