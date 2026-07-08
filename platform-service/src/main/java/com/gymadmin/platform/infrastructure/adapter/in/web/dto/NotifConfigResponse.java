package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

public record NotifConfigResponse(
        Long idCompania,
        Integer diasAntes,
        String canal,
        Boolean activo
) {}
