package com.gymadmin.auth.dto.response;

import java.time.OffsetDateTime;
import java.util.List;

public record BitacoraPagedResponse(
        long total,
        int pagina,
        List<EntryDto> datos
) {
    public record EntryDto(
            Long id,
            Integer idUsuario,
            String nombreUsuario,
            String modulo,
            String accion,
            Integer entidadId,
            String ip,
            OffsetDateTime fecha
    ) {}
}
