package com.gymadmin.attendance.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record PlantillaRequest(
        @NotBlank(message = "tipo es obligatorio")
        String tipo,
        @NotBlank(message = "nombre es obligatorio")
        String nombre,
        @NotBlank(message = "contenido es obligatorio")
        String contenido
) {}
