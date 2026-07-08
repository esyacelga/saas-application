package com.gymadmin.auth.infrastructure.adapter.out.persistence.mapper;

import com.gymadmin.auth.domain.model.UsuarioApp;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.UsuarioAppEntity;
import io.r2dbc.spi.Row;

import java.time.OffsetDateTime;

public final class UsuarioAppMapper {

    private UsuarioAppMapper() {}

    public static UsuarioApp toDomain(UsuarioAppEntity e) {
        return UsuarioApp.builder()
                .id(e.getId()).idPersona(e.getIdPersona()).idCompania(e.getIdCompania())
                .login(e.getLogin()).passwordHash(e.getPasswordHash())
                .requiereCambioPwd(e.getRequiereCambioPwd()).activo(e.getActivo())
                .ultimoAcceso(e.getUltimoAcceso())
                .tokenRecuperacion(e.getTokenRecuperacion()).tokenExpira(e.getTokenExpira())
                .creacionFecha(e.getCreacionFecha()).creacionUsuario(e.getCreacionUsuario())
                .modificaFecha(e.getModificaFecha()).modificaUsuario(e.getModificaUsuario())
                .build();
    }

    /** Maps a JOIN row (identidad.usuarios_app + identidad.personas) to domain model. */
    public static UsuarioApp fromRow(Row row) {
        return UsuarioApp.builder()
                .id(row.get("id", Integer.class))
                .idPersona(row.get("id_persona", Integer.class))
                .nombrePersona(row.get("nombre_persona", String.class))
                .fotoUrlPersona(row.get("foto_url_persona", String.class))
                .sexoPersona(row.get("sexo_persona", String.class))
                .idCompania(row.get("id_compania", Integer.class))
                .login(row.get("login", String.class))
                .passwordHash(row.get("password_hash", String.class))
                .requiereCambioPwd(row.get("requiere_cambio_pwd", Boolean.class))
                .activo(row.get("activo", Boolean.class))
                .ultimoAcceso(row.get("ultimo_acceso", OffsetDateTime.class))
                .tokenRecuperacion(row.get("token_recuperacion", String.class))
                .tokenExpira(row.get("token_expira", OffsetDateTime.class))
                .creacionFecha(row.get("creacion_fecha", OffsetDateTime.class))
                .creacionUsuario(row.get("creacion_usuario", String.class))
                .modificaFecha(row.get("modifica_fecha", OffsetDateTime.class))
                .modificaUsuario(row.get("modifica_usuario", String.class))
                .build();
    }

    public static UsuarioAppEntity toEntity(UsuarioApp d) {
        OffsetDateTime now = OffsetDateTime.now();
        boolean isNew = d.getId() == null;
        return UsuarioAppEntity.builder()
                .id(d.getId()).idPersona(d.getIdPersona()).idCompania(d.getIdCompania())
                .login(d.getLogin()).passwordHash(d.getPasswordHash())
                .requiereCambioPwd(d.getRequiereCambioPwd()).activo(d.getActivo())
                .ultimoAcceso(d.getUltimoAcceso())
                .tokenRecuperacion(d.getTokenRecuperacion()).tokenExpira(d.getTokenExpira())
                .creacionFecha(isNew ? now : d.getCreacionFecha())
                .creacionUsuario(d.getCreacionUsuario() != null ? d.getCreacionUsuario() : "sistema")
                .modificaFecha(isNew ? null : now)
                .modificaUsuario(d.getModificaUsuario())
                .build();
    }
}
