package com.gymadmin.auth.infrastructure.adapter.out.persistence.mapper;

import com.gymadmin.auth.domain.model.RefreshToken;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.RefreshTokenEntity;

import java.time.OffsetDateTime;

public final class RefreshTokenMapper {

    private RefreshTokenMapper() {}

    public static RefreshToken toDomain(RefreshTokenEntity e) {
        return RefreshToken.builder()
                .id(e.getId()).token(e.getToken()).tipoUsuario(e.getTipoUsuario())
                .idUsuario(e.getIdUsuario()).idCompania(e.getIdCompania())
                .expiraEn(e.getExpiraEn())
                .creacionFecha(e.getCreacionFecha()).creacionUsuario(e.getCreacionUsuario())
                .modificaFecha(e.getModificaFecha()).modificaUsuario(e.getModificaUsuario())
                .build();
    }

    public static RefreshTokenEntity toEntity(RefreshToken d) {
        OffsetDateTime now = OffsetDateTime.now();
        boolean isNew = d.getId() == null;
        return RefreshTokenEntity.builder()
                .id(d.getId()).token(d.getToken()).tipoUsuario(d.getTipoUsuario())
                .idUsuario(d.getIdUsuario()).idCompania(d.getIdCompania())
                .expiraEn(d.getExpiraEn())
                .creacionFecha(isNew ? now : d.getCreacionFecha())
                .creacionUsuario(d.getCreacionUsuario() != null ? d.getCreacionUsuario() : "sistema")
                .modificaFecha(isNew ? null : now)
                .modificaUsuario(d.getModificaUsuario())
                .build();
    }
}
