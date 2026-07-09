package com.gymadmin.billing.domain.model;

import java.time.LocalDateTime;

public record AuditoriaEmision(
        Long idComprobante,
        String claveAcceso,
        String estado,
        LocalDateTime fechaEmision,
        LocalDateTime fechaAutorizacion,
        Integer intentosSri,
        String numeroAutorizacion,
        String ambiente
) {}
