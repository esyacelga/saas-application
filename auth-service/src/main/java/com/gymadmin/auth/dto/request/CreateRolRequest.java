package com.gymadmin.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CreateRolRequest(
        @NotBlank String nombre,
        String descripcion,
        Integer idCompania,
        Integer idSucursal
) {}
