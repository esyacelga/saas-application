package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import java.time.LocalDateTime;

public record MiSucursalResponse(
        Long id,
        String nombre,
        String direccion,
        Boolean esPrincipal,
        String qrToken,
        LocalDateTime qrTokenExpira
) {}
