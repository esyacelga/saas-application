package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CaracteristicaRequest(
        @NotBlank(message = "codigo is required")
        String codigo,

        @NotBlank(message = "nombre is required")
        String nombre,

        @NotBlank(message = "modulo is required")
        String modulo
) {}
