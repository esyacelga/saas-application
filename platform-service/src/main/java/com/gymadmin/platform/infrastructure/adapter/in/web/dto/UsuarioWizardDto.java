package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UsuarioWizardDto(

        // Optional: present when persona already exists in identidad.personas
        Long idPersona,

        // Required only when idPersona is absent — used to find or create the persona.
        // Cuando idPersona viene, resolverIdPersona() ignora ci/nombre (la persona ya existe),
        // por eso NO pueden ser @NotBlank incondicional: se validan en identidadCompleta().
        String ci,

        String nombre,

        String telefono,

        @NotBlank(message = "correo is required")
        @Email(message = "correo must be a valid email")
        String correo,

        @NotBlank(message = "password is required")
        @Size(min = 8, message = "password must be at least 8 characters")
        String password
) {

    /**
     * ci y nombre son obligatorios únicamente cuando no se envía idPersona
     * (hay que crear/buscar la persona por cédula). Si idPersona viene, la
     * persona ya existe y estos campos son irrelevantes.
     */
    @JsonIgnore
    @AssertTrue(message = "ci and nombre are required when idPersona is absent")
    public boolean isIdentidadCompleta() {
        if (idPersona != null) {
            return true;
        }
        return ci != null && !ci.isBlank() && nombre != null && !nombre.isBlank();
    }
}
