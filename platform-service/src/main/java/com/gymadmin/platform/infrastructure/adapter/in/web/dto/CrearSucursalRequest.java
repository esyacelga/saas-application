package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CrearSucursalRequest(
        @NotBlank(message = "nombre is required")
        String nombre,

        String direccion,
        Boolean esPrincipal
) {}
