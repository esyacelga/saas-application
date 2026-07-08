package com.gymadmin.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PasswordResetRequestDto(
        @NotBlank String correo,
        @NotNull Integer idCompania,
        @NotBlank String tipo          // staff | cliente
) {}
