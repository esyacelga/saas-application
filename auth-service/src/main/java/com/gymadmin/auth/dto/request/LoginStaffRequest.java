package com.gymadmin.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record LoginStaffRequest(
        @NotBlank String correo,
        @NotBlank String password,
        @NotNull Integer idCompania
) {}
