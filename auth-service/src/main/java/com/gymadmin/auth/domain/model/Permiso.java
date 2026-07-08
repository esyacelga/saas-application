package com.gymadmin.auth.domain.model;

import lombok.*;
import java.time.OffsetDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Permiso {
    private Integer id;
    private Integer idCompania;
    private Integer idSucursal;
    private String nombre;
    private String descripcion;
    private String modulo;
    private OffsetDateTime creacionFecha;
    @Builder.Default private String creacionUsuario = "sistema";
    private OffsetDateTime modificaFecha;
    private String modificaUsuario;
}
