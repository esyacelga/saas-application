package com.gymadmin.core.infrastructure.adapter.in.web.dto;

import com.gymadmin.core.domain.model.Membresia;
import jakarta.validation.constraints.NotNull;

/**
 * Body para {@code POST /membresias/{id}/rechazar}. El motivo es obligatorio y
 * queda validado por el catálogo cerrado del enum {@link Membresia.MotivoEliminacion}
 * (Jackson responde con 400 si el valor no es uno de: SOCIO_CAMBIO_OPINION,
 * ERROR_DE_VENTA, DUPLICADA, DATOS_INCORRECTOS, OTRO).
 */
public record RechazarMembresiaRequest(
        @NotNull Membresia.MotivoEliminacion motivoEliminacion
) {}
