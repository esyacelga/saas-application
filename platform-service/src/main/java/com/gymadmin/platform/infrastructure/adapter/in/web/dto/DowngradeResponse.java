package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DowngradeResponse(
        Long idCompaniaPlanNuevo,
        String estado,
        LocalDate efectivoDe,
        BigDecimal creditoGenerado
) {}
