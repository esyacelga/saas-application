package com.gymadmin.attendance.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record RegistrarOverrideRequest(
        @JsonProperty("id_cliente")
        @NotNull(message = "id_cliente es obligatorio")
        Integer idCliente,
        @JsonProperty("id_sucursal")
        Integer idSucursal,
        LocalDate fecha,
        @JsonProperty("hora_entrada")
        LocalTime horaEntrada,
        @JsonProperty("motivo_override")
        @NotBlank(message = "motivo_override es obligatorio")
        String motivoOverride
) {}
