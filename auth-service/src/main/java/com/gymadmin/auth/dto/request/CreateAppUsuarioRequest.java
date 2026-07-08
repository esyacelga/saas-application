package com.gymadmin.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAppUsuarioRequest(
        @NotNull Integer idPersona,
        @NotBlank String login,
        @NotBlank @Size(min = 8) String password,
        Integer idCompania
) {}
