package com.gymadmin.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OAuthGoogleRequest(
        @NotBlank String idToken,
        @NotNull Integer idCompania
) {}
