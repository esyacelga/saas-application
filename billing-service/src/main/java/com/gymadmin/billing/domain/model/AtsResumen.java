package com.gymadmin.billing.domain.model;

import java.math.BigDecimal;

public record AtsResumen(
        Integer anio,
        Integer mes,
        Integer totalVentas,
        BigDecimal baseImponible,
        BigDecimal totalIva,
        BigDecimal totalFacturado
) {}
