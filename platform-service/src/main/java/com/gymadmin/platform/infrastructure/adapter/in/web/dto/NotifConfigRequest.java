package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record NotifConfigRequest(
        @NotNull(message = "configs is required")
        List<ConfigEntry> configs
) {
    public record ConfigEntry(
            Integer diasAntes,
            String canal,
            Boolean activo
    ) {}
}
