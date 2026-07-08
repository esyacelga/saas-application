package com.gymadmin.finance.domain.model;

import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoriaEgreso {

    private Integer id;
    private Integer idCompania;
    private Integer idSucursal;
    private String nombre;
    private Boolean activo;
    private Boolean eliminado;
    private Instant creacionFecha;
    private String creacionUsuario;
    private Instant modificaFecha;
    private String modificaUsuario;
}
