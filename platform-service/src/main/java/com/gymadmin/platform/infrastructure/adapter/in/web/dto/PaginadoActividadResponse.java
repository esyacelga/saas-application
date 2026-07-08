package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import java.util.List;

public record PaginadoActividadResponse(
        long total,
        int pagina,
        List<ActividadPlataformaResponse> datos
) {}
