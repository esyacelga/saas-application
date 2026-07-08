package com.gymadmin.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OAuthFacebookRequest(
        @NotBlank String accessToken,
        @NotNull Integer idCompania
) {}
