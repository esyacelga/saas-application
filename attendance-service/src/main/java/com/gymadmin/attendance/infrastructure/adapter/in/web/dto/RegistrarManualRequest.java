package com.gymadmin.attendance.infrastructure.adapter.in.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record RegistrarManualRequest(
        @JsonProperty("id_cliente")
        @NotNull(message = "id_cliente es obligatorio")
        Integer idCliente,
        LocalDate fecha,
        @JsonProperty("hora_entrada")
        LocalTime horaEntrada
) {}
