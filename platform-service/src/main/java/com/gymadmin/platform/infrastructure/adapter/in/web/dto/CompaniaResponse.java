package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import java.time.Instant;
import java.time.LocalDate;

public record CompaniaResponse(
        Long id,
        String nombre,
        String ruc,
        String telefono,
        String whatsapp,
        String correo,
        String logoUrl,
        Boolean activo,
        PlanActivoDto planActivo,
        // Opt-in de WhatsApp del dueño (Fase 6). Se expone en la lectura para que el panel pueda
        // hidratar el switch de consentimiento; se escribe SOLO vía PATCH /companias/{id}/consentimiento-wa.
        boolean aceptaWhatsapp,
        Instant fechaConsentimientoWa
) {
    public record PlanActivoDto(
            String nombre,
            String estado,
            LocalDate fechaFin,
            Long diasRestantes
    ) {}
}
