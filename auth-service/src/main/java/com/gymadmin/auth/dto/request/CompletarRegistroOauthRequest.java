package com.gymadmin.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CompletarRegistroOauthRequest(
        @NotBlank
        @Pattern(regexp = "google|facebook", message = "provider debe ser 'google' o 'facebook'")
        String provider,

        @NotBlank
        String token,

        @NotNull
        @Positive
        Integer idCompania,

        @NotBlank
        @Pattern(regexp = "^\\d{10}$|^\\d{13}$", message = "CI/RUC debe tener 10 o 13 digitos")
        String ci,

        @NotBlank
        @Size(min = 3, max = 200)
        String nombre,

        String telefono
) {}
