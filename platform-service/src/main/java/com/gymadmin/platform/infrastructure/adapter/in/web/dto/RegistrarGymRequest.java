package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegistrarGymRequest(
        @NotBlank(message = "nombre is required")
        String nombre,

        @NotBlank(message = "ruc is required")
        String ruc,

        String logoUrl,
        String telefono,
        String whatsapp,

        @Email(message = "correo must be a valid email")
        String correo,

        @NotNull(message = "idPlan is required")
        Long idPlan,

        String nombreSucursal,
        String direccionSucursal
) {}
