package com.gymadmin.finance.domain.model;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ingreso {

    private Integer id;
    private Integer idCompania;
    private Integer idSucursal;
    private Integer idCategoria;
    private Integer idMembresia;
    private Integer idVenta;
    private BigDecimal monto;
    private String descripcion;
    private LocalDate fecha;
    private Integer idUsuarioRegistro;
    private Boolean eliminado;
    private Instant creacionFecha;
    private String creacionUsuario;
    private Instant modificaFecha;
    private String modificaUsuario;
}
