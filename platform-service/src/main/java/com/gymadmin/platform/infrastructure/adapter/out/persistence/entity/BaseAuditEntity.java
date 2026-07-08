package com.gymadmin.platform.infrastructure.adapter.out.persistence.entity;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Column;

import java.time.OffsetDateTime;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class BaseAuditEntity {

    @CreatedDate
    @Column("creacion_fecha")
    private OffsetDateTime creacionFecha;

    @CreatedBy
    @Column("creacion_usuario")
    private String creacionUsuario;

    @LastModifiedDate
    @Column("modifica_fecha")
    private OffsetDateTime modificaFecha;

    @LastModifiedBy
    @Column("modifica_usuario")
    private String modificaUsuario;

    @Builder.Default
    @Column("eliminado")
    private Boolean eliminado = false;
}
