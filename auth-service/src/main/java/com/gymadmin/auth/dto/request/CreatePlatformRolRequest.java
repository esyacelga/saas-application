package com.gymadmin.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePlatformRolRequest(
        @NotBlank
        String nombre,
        String descripcion,
        @NotNull
        Integer idCompania,
        Integer idSucursal

) {
}
