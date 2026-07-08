package com.gymadmin.auth.domain.model;

import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioPlataforma {
    private Integer id;
    private Integer idPersona;
    private String nombrePersona;      // populated via JOIN in persistence adapter
    private String fotoUrlPersona;     // populated via JOIN in persistence adapter
    private String correo;
    private String passwordHash;
    private String rol;
    @Builder.Default
    private Boolean activo = true;
    private OffsetDateTime ultimoAcceso;
    private OffsetDateTime creacionFecha;
    @Builder.Default
    private String creacionUsuario = "sistema";
    private OffsetDateTime modificaFecha;
    private String modificaUsuario;
}
