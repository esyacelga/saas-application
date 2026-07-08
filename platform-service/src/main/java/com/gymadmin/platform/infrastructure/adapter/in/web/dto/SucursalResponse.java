package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import java.time.LocalDateTime;

public record SucursalResponse(
        Long id,
        Long idCompania,
        String nombre,
        String direccion,
        Boolean esPrincipal,
        Boolean activo,
        String qrToken,
        LocalDateTime qrTokenExpira
) {}
