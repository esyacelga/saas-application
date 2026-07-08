package com.gymadmin.auth.dto.response;

import java.time.OffsetDateTime;

public record PlatformUsuarioResponse(
        Integer id,
        String nombre,
        String correo,
        String rolPlataforma,
        Boolean activo,
        OffsetDateTime ultimoAcceso,
        String fotoUrl
) {
}
