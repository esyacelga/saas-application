package com.gymadmin.core.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;

public record ActualizarClienteRequest(
        BigDecimal pesoKg,
        BigDecimal alturaCm,
        String objetivos,
        String lesiones,
        String telefono
) {}
