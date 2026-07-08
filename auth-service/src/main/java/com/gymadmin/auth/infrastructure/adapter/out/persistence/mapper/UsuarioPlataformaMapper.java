package com.gymadmin.auth.infrastructure.adapter.out.persistence.mapper;

import com.gymadmin.auth.domain.model.UsuarioPlataforma;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.UsuarioPlataformaEntity;
import io.r2dbc.spi.Row;

import java.time.OffsetDateTime;

public final class UsuarioPlataformaMapper {

    private UsuarioPlataformaMapper() {}

    public static UsuarioPlataforma toDomain(UsuarioPlataformaEntity e) {
        return UsuarioPlataforma.builder()
                .id(e.getId()).idPersona(e.getIdPersona()).correo(e.getCorreo())
                .passwordHash(e.getPasswordHash()).rol(e.getRol()).activo(e.getActivo())
                .ultimoAcceso(e.getUltimoAcceso())
                .creacionFecha(e.getCreacionFecha()).creacionUsuario(e.getCreacionUsuario())
                .modificaFecha(e.getModificaFecha()).modificaUsuario(e.getModificaUsuario())
                .build();
    }

    /** Maps a JOIN row (saas.usuarios_plataforma + identidad.personas) to domain model. */
    public static UsuarioPlataforma fromRow(Row row) {
        return UsuarioPlataforma.builder()
                .id(row.get("id", Integer.class))
                .idPersona(row.get("id_persona", Integer.class))
                .nombrePersona(row.get("nombre_persona", String.class))
                .fotoUrlPersona(row.get("foto_url_persona", String.class))
                .correo(row.get("correo", String.class))
                .passwordHash(row.get("password_hash", String.class))
                .rol(row.get("rol", String.class))
                .activo(row.get("activo", Boolean.class))
                .ultimoAcceso(row.get("ultimo_acceso", OffsetDateTime.class))
                .creacionFecha(row.get("creacion_fecha", OffsetDateTime.class))
                .creacionUsuario(row.get("creacion_usuario", String.class))
                .modificaFecha(row.get("modifica_fecha", OffsetDateTime.class))
                .modificaUsuario(row.get("modifica_usuario", String.class))
                .build();
    }

    public static UsuarioPlataformaEntity toEntity(UsuarioPlataforma d) {
        OffsetDateTime now = OffsetDateTime.now();
        boolean isNew = d.getId() == null;
        return UsuarioPlataformaEntity.builder()
                .id(d.getId()).idPersona(d.getIdPersona()).correo(d.getCorreo())
                .passwordHash(d.getPasswordHash()).rol(d.getRol()).activo(d.getActivo())
                .ultimoAcceso(d.getUltimoAcceso())
                .creacionFecha(isNew ? now : d.getCreacionFecha())
                .creacionUsuario(d.getCreacionUsuario() != null ? d.getCreacionUsuario() : "sistema")
                .modificaFecha(isNew ? null : now)
                .modificaUsuario(d.getModificaUsuario())
                .build();
    }
}
