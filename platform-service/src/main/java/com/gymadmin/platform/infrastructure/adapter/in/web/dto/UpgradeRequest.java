package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

public record UpgradeRequest(
        @NotNull(message = "idPlanNuevo is required")
        Long idPlanNuevo
) {}
