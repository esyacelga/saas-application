package com.gymadmin.auth.domain.model;

import lombok.*;
import java.time.OffsetDateTime;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class RefreshToken {
    private Long id;
    private String token;
    private String tipoUsuario;
    private Integer idUsuario;
    private Integer idCompania;
    private OffsetDateTime expiraEn;
    private OffsetDateTime creacionFecha;
    @Builder.Default private String creacionUsuario = "sistema";
    private OffsetDateTime modificaFecha;
    private String modificaUsuario;

    public boolean isExpired() {
        return OffsetDateTime.now().isAfter(expiraEn);
    }
}
