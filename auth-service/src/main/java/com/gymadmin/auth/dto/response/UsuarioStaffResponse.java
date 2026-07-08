package com.gymadmin.auth.dto.response;

import java.time.OffsetDateTime;

public record UsuarioStaffResponse(
        Integer id,
        Integer idPersona,
        String nombre,
        String correo,
        String fotoUrl,
        Integer idRol,
        String nombreRol,
        Boolean activo,
        OffsetDateTime ultimoAcceso
) {}
