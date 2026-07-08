package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record SuspenderRequest(
        @NotBlank(message = "motivo is required")
        String motivo
) {}
