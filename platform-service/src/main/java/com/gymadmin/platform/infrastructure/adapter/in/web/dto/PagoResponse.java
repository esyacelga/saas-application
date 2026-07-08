package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PagoResponse(
        Long id,
        Long idCompaniaPlan,
        BigDecimal monto,
        LocalDate fechaPago,
        LocalDate periodoDesde,
        LocalDate periodoHasta,
        String metodoPago,
        String tipoPago,
        String estado,
        String referencia
) {}
