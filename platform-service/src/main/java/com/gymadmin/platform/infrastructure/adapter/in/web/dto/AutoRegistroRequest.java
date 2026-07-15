package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Auto-registro PÚBLICO de un gimnasio (endpoint sin auth).
 *
 * A diferencia de {@link RegistrarGymWizardRequest} (que usa el operador de
 * plataforma), aquí el <b>RUC es opcional</b>: un dueño que solo quiere probar la
 * app no está obligado a dar datos tributarios. El RUC se solicita más tarde, en el
 * wizard de configuración de facturación, cuando el gym decide facturar
 * (disclosure progresivo). Tampoco se piden teléfono ni WhatsApp desde el registro.
 *
 * El registro por operador conserva su propio DTO con {@code @NotBlank ruc}: no se
 * debilita esa validación.
 */
public record AutoRegistroRequest(

        @NotBlank(message = "nombre is required")
        String nombre,

        // Opcional en el auto-registro público. La columna tenant.companias.ruc es
        // nullable desde la migración GYM-002-2.
        String ruc,

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
        UsuarioWizardDto usuarioPrincipal
) {}
