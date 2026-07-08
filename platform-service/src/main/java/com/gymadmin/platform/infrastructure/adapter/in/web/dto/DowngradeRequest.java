package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

public record DowngradeRequest(
        @NotNull(message = "idPlanNuevo is required")
        Long idPlanNuevo
) {}
