package com.gymadmin.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePermisoRequest(
        @NotBlank @Size(max = 100) String nombre,
        @NotBlank @Size(max = 50) String modulo,
        @Size(max = 255) String descripcion,
        @NotNull Integer idCompania,
        @NotNull Integer idSucursal
) {}
