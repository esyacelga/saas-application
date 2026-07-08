package com.gymadmin.attendance.domain.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
public class Asistencia {
    private Long id;
    private Integer idCompania;
    private Integer idSucursal;
    private Integer idCliente;
    private Integer idMembresia;
    private LocalDate fecha;
    private LocalTime horaEntrada;
    private String metodoRegistro;
    private Boolean eliminado;
    private Instant creacionFecha;
    private String creacionUsuario;
    private Instant modificaFecha;
    private String modificaUsuario;
}
