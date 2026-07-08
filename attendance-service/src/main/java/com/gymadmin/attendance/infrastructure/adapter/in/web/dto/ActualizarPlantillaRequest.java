package com.gymadmin.attendance.infrastructure.adapter.in.web.dto;

public record ActualizarPlantillaRequest(
        String contenido,
        Boolean activo,
        String nombre
) {}
