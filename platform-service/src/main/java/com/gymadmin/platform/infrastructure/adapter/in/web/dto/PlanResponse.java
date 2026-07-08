package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record PlanResponse(
        Long id,
        String nombre,
        String descripcion,
        BigDecimal precioMensual,
        Boolean activo,
        List<CaracteristicaDto> caracteristicas
) {}
