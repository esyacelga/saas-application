package com.gymadmin.attendance.domain.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.OffsetDateTime;

@Getter
@Setter
public class MensajeLog {
    private Long id;
    private Integer idCompania;
    private Integer idSucursal;
    private Integer idCliente;
    private Integer idPlantilla;
    private String tipo;
    private String canal;
    private String contenido;
    private String estado;
    private OffsetDateTime fechaProgramada;
    private OffsetDateTime fechaEnvio;
    private Integer idUsuarioEnvio;
    private Boolean eliminado;
    private Instant creacionFecha;
    private String creacionUsuario;
    private Instant modificaFecha;
    private String modificaUsuario;
}
