package com.gymadmin.billing.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

public record PeriodoResumen(
        LocalDate desde,
        LocalDate hasta,
        Long totalEmitidos,
        Long totalAutorizados,
        Long totalError,
        BigDecimal subtotalSinIva,
        BigDecimal totalIva,
        BigDecimal totalFacturado,
        Map<String, Long> porEstado
) {}
