package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

public record ModuloCheckResponse(
        Boolean permitido,
        String plan,
        String razon
) {}
