package com.gymadmin.attendance.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record EnviarMensajeRequest(
        @NotNull(message = "id_cliente es obligatorio")
        Integer idCliente,
        @NotBlank(message = "canal es obligatorio")
        String canal,
        @NotNull(message = "id_plantilla es obligatorio")
        Integer idPlantilla
) {}
