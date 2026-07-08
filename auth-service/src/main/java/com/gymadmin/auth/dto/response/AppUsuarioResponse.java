package com.gymadmin.auth.dto.response;

import java.time.OffsetDateTime;

public record AppUsuarioResponse(
        Integer id,
        String login,
        Boolean activo,
        OffsetDateTime ultimoAcceso
) {}
