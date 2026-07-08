package com.gymadmin.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdatePlatformUsuarioRequest(
        @NotBlank @Pattern(regexp = "super_admin|soporte|viewer") String rol
) {
}
