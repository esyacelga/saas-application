package com.gymadmin.auth.infrastructure.adapter.out.persistence.mapper;

import com.gymadmin.auth.domain.model.UsuarioStaff;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.UsuarioStaffEntity;
import io.r2dbc.spi.Row;

import java.time.OffsetDateTime;

public final class UsuarioStaffMapper {

    private UsuarioStaffMapper() {}

    public static UsuarioStaff toDomain(UsuarioStaffEntity e) {
        return UsuarioStaff.builder()
                .id(e.getId()).idCompania(e.getIdCompania()).idSucursal(e.getIdSucursal())
                .idRol(e.getIdRol()).idPersona(e.getIdPersona()).correo(e.getCorreo())
                .passwordHash(e.getPasswordHash()).activo(e.getActivo())
                .ultimoAcceso(e.getUltimoAcceso())
                .creacionFecha(e.getCreacionFecha()).creacionUsuario(e.getCreacionUsuario())
                .modificaFecha(e.getModificaFecha()).modificaUsuario(e.getModificaUsuario())
                .build();
    }

    /** Maps a JOIN row (seguridad.usuarios + seguridad.roles + identidad.personas) to domain model. */
    public static UsuarioStaff fromRow(Row row) {
        return UsuarioStaff.builder()
                .id(row.get("id", Integer.class))
                .idCompania(row.get("id_compania", Integer.class))
                .idSucursal(row.get("id_sucursal", Integer.class))
                .idRol(row.get("id_rol", Integer.class))
                .nombreRol(row.get("nombre_rol", String.class))
                .idPersona(row.get("id_persona", Integer.class))
                .nombrePersona(row.get("nombre_persona", String.class))
                .fotoUrlPersona(row.get("foto_url_persona", String.class))
                .correo(row.get("correo", String.class))
                .passwordHash(row.get("password_hash", String.class))
                .requiereCambioPwd(row.get("requiere_cambio_pwd", Boolean.class))
                .activo(row.get("activo", Boolean.class))
                .ultimoAcceso(row.get("ultimo_acceso", OffsetDateTime.class))
                .creacionFecha(row.get("creacion_fecha", OffsetDateTime.class))
                .creacionUsuario(row.get("creacion_usuario", String.class))
                .modificaFecha(row.get("modifica_fecha", OffsetDateTime.class))
                .modificaUsuario(row.get("modifica_usuario", String.class))
                .build();
    }

    public static UsuarioStaffEntity toEntity(UsuarioStaff d) {
        OffsetDateTime now = OffsetDateTime.now();
        boolean isNew = d.getId() == null;
        return UsuarioStaffEntity.builder()
                .id(d.getId()).idCompania(d.getIdCompania()).idSucursal(d.getIdSucursal())
                .idRol(d.getIdRol()).idPersona(d.getIdPersona()).correo(d.getCorreo())
                .passwordHash(d.getPasswordHash())
                .requiereCambioPwd(d.getRequiereCambioPwd())
                .activo(d.getActivo())
                .ultimoAcceso(d.getUltimoAcceso())
                .creacionFecha(isNew ? now : d.getCreacionFecha())
                .creacionUsuario(d.getCreacionUsuario() != null ? d.getCreacionUsuario() : "sistema")
                .modificaFecha(isNew ? null : now)
                .modificaUsuario(d.getModificaUsuario())
                .build();
    }
}
