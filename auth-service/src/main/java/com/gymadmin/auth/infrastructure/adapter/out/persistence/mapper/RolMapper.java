package com.gymadmin.auth.infrastructure.adapter.out.persistence.mapper;

import com.gymadmin.auth.domain.model.Rol;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.RolEntity;

import java.time.OffsetDateTime;

public final class RolMapper {

    private RolMapper() {}

    public static Rol toDomain(RolEntity e) {
        return Rol.builder()
                .id(e.getId()).idCompania(e.getIdCompania()).idSucursal(e.getIdSucursal())
                .nombre(e.getNombre()).descripcion(e.getDescripcion())
                .creacionFecha(e.getCreacionFecha()).creacionUsuario(e.getCreacionUsuario())
                .modificaFecha(e.getModificaFecha()).modificaUsuario(e.getModificaUsuario())
                .build();
    }

    public static RolEntity toEntity(Rol d) {
        OffsetDateTime now = OffsetDateTime.now();
        boolean isNew = d.getId() == null;
        return RolEntity.builder()
                .id(d.getId()).idCompania(d.getIdCompania()).idSucursal(d.getIdSucursal())
                .nombre(d.getNombre()).descripcion(d.getDescripcion())
                .creacionFecha(isNew ? now : d.getCreacionFecha())
                .creacionUsuario(d.getCreacionUsuario() != null ? d.getCreacionUsuario() : "sistema")
                .modificaFecha(isNew ? null : now)
                .modificaUsuario(d.getModificaUsuario())
                .build();
    }
}
