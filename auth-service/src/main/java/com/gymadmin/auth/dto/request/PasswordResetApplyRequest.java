package com.gymadmin.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetApplyRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8) String nuevaPassword
) {}
