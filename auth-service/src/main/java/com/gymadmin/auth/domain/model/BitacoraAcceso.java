package com.gymadmin.auth.domain.model;

import lombok.*;
import java.time.OffsetDateTime;
import java.util.Map;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BitacoraAcceso {
    private Long id;
    private Integer idCompania;
    private Integer idSucursal;
    private Integer idUsuario;
    private String modulo;
    private String accion;
    private Integer entidadId;
    private Map<String, Object> detalle;
    private String ip;
    private OffsetDateTime fecha;
    private OffsetDateTime creacionFecha;
    @Builder.Default private String creacionUsuario = "sistema";
    private OffsetDateTime modificaFecha;
    private String modificaUsuario;
}
