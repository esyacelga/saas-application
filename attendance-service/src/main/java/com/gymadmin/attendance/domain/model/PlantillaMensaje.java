package com.gymadmin.attendance.domain.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
public class PlantillaMensaje {
    private Integer id;
    private Integer idCompania;
    private Integer idSucursal;
    private String tipo;
    private String nombre;
    private String contenido;
    private Boolean activo;
    private Boolean eliminado;
    private Instant creacionFecha;
    private String creacionUsuario;
    private Instant modificaFecha;
    private String modificaUsuario;
}
