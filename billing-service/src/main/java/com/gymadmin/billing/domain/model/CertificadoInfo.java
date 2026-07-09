package com.gymadmin.billing.domain.model;

import java.time.LocalDate;

public record CertificadoInfo(
        Long id,
        Integer idCompania,
        Integer idSucursal,
        LocalDate fechaVencimiento
) {}
