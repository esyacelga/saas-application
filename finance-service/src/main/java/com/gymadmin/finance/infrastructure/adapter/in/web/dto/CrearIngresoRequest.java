package com.gymadmin.finance.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CrearIngresoRequest(
        @NotNull(message = "id_categoria es obligatorio")
        Integer idCategoria,

        @NotNull(message = "monto es obligatorio")
        @DecimalMin(value = "0.01", message = "El monto debe ser mayor a 0")
        BigDecimal monto,

        String descripcion,

        LocalDate fecha,

        Integer idMembresia,

        Integer idVenta,

        Integer idSucursal
) {}
