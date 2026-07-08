package com.gymadmin.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreatePlatformUsuarioRequest(
        @NotNull Integer idPersona,
        @NotBlank @Email String correo,
        @NotBlank @Size(min = 8) String password,
        @NotBlank @Pattern(regexp = "super_admin|soporte|viewer") String rol
) {}
