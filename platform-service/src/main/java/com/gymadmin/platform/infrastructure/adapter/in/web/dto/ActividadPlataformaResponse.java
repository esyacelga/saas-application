package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import java.time.OffsetDateTime;

public record ActividadPlataformaResponse(
        Long id,
        String tipoEvento,
        String modulo,
        Long entidadId,
        String entidadNombre,
        String detalle,
        String usuario,
        String ip,
        OffsetDateTime fecha
) {}
