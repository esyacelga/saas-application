package com.gymadmin.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginAppRequest(
        @NotBlank String login,
        @NotBlank String password,
        @NotNull Integer idCompania
) {}
