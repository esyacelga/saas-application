package com.gymadmin.auth.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record PersonaResponse(
        Integer id,
        String ci,
        String nombre,
        String telefono,
        String correo,
        String fotoUrl,
        String sexo,
        LocalDate fechaNacimiento,
        OffsetDateTime createdAt
) {}
