package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record RegistrarGymWizardRequest(

        @NotBlank(message = "nombre is required")
        String nombre,

        @NotBlank(message = "ruc is required")
        String ruc,

        String logoUrl,

        @Email(message = "correo must be a valid email")
        String correo,

        String telefono,
        String whatsapp,

        @NotNull(message = "idPlan is required")
        Long idPlan,

        @NotBlank(message = "nombreSucursal is required")
        String nombreSucursal,

        String direccionSucursal,

        @NotNull(message = "usuarioPrincipal is required")
        @Valid
        UsuarioWizardDto usuarioPrincipal,

        @Valid
        List<UsuarioWizardDto> usuariosAdicionales
) {}
