package com.gymadmin.core.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RegistrarClienteRequest(
        @NotBlank String ci,
        @NotBlank String nombre,
        String telefono,
        String correo,
        LocalDate fechaNacimiento,
        BigDecimal pesoKg,
        BigDecimal alturaCm,
        String objetivos,
        String lesiones,
        @NotNull Long idSucursal,
        String sexo
) {}
