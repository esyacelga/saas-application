package com.gymadmin.attendance.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

public record RegistrarAppRequest(
        @JsonProperty("id_sucursal")
        @NotNull(message = "id_sucursal es obligatorio")
        Integer idSucursal,

        @JsonProperty("nombre_sucursal")
        String nombreSucursal
) {}
