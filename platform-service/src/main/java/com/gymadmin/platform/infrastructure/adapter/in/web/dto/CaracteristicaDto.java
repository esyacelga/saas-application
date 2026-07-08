package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

public record CaracteristicaDto(
        Long id,
        String codigo,
        String nombre,
        String modulo,
        Boolean activo
) {}
