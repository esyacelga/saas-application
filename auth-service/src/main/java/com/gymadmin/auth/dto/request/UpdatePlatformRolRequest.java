package com.gymadmin.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdatePlatformRolRequest(
        @NotBlank String nombre,
        String descripcion
) {}
