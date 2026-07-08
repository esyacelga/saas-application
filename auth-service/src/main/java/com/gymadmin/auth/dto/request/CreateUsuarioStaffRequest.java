package com.gymadmin.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateUsuarioStaffRequest(
        @NotNull Integer idPersona,
        @NotBlank @Email String correo,
        @NotNull Integer idRol,
        Integer idSucursal,
        @NotBlank String passwordTemporal,
        Integer idCompania
) {}
