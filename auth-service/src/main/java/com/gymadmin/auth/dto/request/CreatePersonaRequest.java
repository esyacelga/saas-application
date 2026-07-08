package com.gymadmin.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record CreatePersonaRequest(
        @NotBlank String ci,
        @NotBlank String nombre,
        String telefono,
        String correo,
        String sexo,
        String fotoUrl,
        LocalDate fechaNacimiento
) {}
