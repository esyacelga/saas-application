package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * REQ-SAAS-001 (RN-08): body para
 * {@code POST /plataforma/pagos-pendientes/{id}/rechazar}. El motivo es
 * obligatorio y debe tener al menos 10 caracteres.
 */
public record RechazarPagoRequest(
        @NotBlank(message = "motivo es obligatorio")
        @Size(min = 10, max = 500, message = "motivo debe tener entre 10 y 500 caracteres")
        String motivo
) {}
