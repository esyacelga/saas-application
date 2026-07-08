package com.gymadmin.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegistroAppRequest(
        @NotBlank @Size(min = 2) String nombre,
        @NotBlank @Email String correo,
        @NotBlank @Size(min = 8) String password,
        @NotNull Integer idCompania,
        String telefono
) {}