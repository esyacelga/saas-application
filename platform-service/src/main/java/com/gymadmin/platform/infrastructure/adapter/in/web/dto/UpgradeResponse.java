package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;

public record UpgradeResponse(
        Long idCompaniaPlanNuevo,
        BigDecimal creditoAplicado,
        BigDecimal montoAPagar,
        Boolean planAnteriorCancelado
) {}
