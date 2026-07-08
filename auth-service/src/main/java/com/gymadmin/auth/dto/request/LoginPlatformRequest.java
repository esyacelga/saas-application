package com.gymadmin.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginPlatformRequest(
        @NotBlank String correo,
        @NotBlank String password
) {}
