package com.gymadmin.auth.infrastructure.adapter.out.persistence.mapper;

import com.gymadmin.auth.domain.model.Permiso;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.PermisoEntity;
import io.r2dbc.spi.Row;

import java.time.OffsetDateTime;

public final class PermisoMapper {

    private PermisoMapper() {}

    public static Permiso toDomain(PermisoEntity e) {
        return Permiso.builder()
                .id(e.getId()).idCompania(e.getIdCompania()).idSucursal(e.getIdSucursal())
                .nombre(e.getNombre()).descripcion(e.getDescripcion()).modulo(e.getModulo())
                .creacionFecha(e.getCreacionFecha()).creacionUsuario(e.getCreacionUsuario())
                .modificaFecha(e.getModificaFecha()).modificaUsuario(e.getModificaUsuario())
                .build();
    }

    public static Permiso fromRow(Row row) {
        return Permiso.builder()
                .id(row.get("id", Integer.class))
                .idCompania(row.get("id_compania", Integer.class))
                .idSucursal(row.get("id_sucursal", Integer.class))
                .nombre(row.get("nombre", String.class))
                .descripcion(row.get("descripcion", String.class))
                .modulo(row.get("modulo", String.class))
                .build();
    }

    public static PermisoEntity toEntity(Permiso d) {
        OffsetDateTime now = OffsetDateTime.now();
        boolean isNew = d.getId() == null;
        return PermisoEntity.builder()
                .id(d.getId()).idCompania(d.getIdCompania()).idSucursal(d.getIdSucursal())
                .nombre(d.getNombre()).descripcion(d.getDescripcion()).modulo(d.getModulo())
                .creacionFecha(isNew ? now : d.getCreacionFecha())
                .creacionUsuario(d.getCreacionUsuario() != null ? d.getCreacionUsuario() : "sistema")
                .modificaFecha(isNew ? null : now)
                .modificaUsuario(d.getModificaUsuario())
                .build();
    }
}
