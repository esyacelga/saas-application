package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AsignarCaracteristicasRequest(
        @NotNull(message = "caracteristicaIds is required")
        List<Long> caracteristicaIds
) {}
