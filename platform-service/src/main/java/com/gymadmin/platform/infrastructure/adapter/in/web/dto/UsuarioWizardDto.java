package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UsuarioWizardDto(

        // Optional: present when persona already exists in identidad.personas
        Long idPersona,

        // Required when idPersona is absent — used to find or create the persona
        @NotBlank(message = "ci is required")
        String ci,

        @NotBlank(message = "nombre is required")
        String nombre,

        String telefono,

        @NotBlank(message = "correo is required")
        @Email(message = "correo must be a valid email")
        String correo,

        @NotBlank(message = "password is required")
        @Size(min = 8, message = "password must be at least 8 characters")
        String password
) {}
