package com.gymadmin.auth.domain.model;

import lombok.*;

import java.time.OffsetDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioApp {
    private Integer id;
    private Integer idPersona;
    private String nombrePersona;      // populated via JOIN in persistence adapter
    private String fotoUrlPersona;     // populated via JOIN in persistence adapter
    private String sexoPersona;        // populated via JOIN in persistence adapter
    private Integer idCompania;
    private String login;
    private String passwordHash;
    private Boolean requiereCambioPwd;
    @Builder.Default
    private Boolean activo = true;
    private OffsetDateTime ultimoAcceso;
    private String tokenRecuperacion;
    private OffsetDateTime tokenExpira;
    private OffsetDateTime creacionFecha;
    @Builder.Default
    private String creacionUsuario = "sistema";
    private OffsetDateTime modificaFecha;
    private String modificaUsuario;
}
