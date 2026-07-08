package com.gymadmin.auth.dto.request;

import java.time.LocalDate;

public record UpdatePersonaRequest(
        String nombre,
        String telefono,
        String correo,
        String fotoUrl,
        String sexo,
        String ci,
        LocalDate fechaNacimiento
) {}
