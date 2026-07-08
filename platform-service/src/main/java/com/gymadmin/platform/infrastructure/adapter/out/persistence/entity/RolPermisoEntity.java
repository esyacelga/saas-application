package com.gymadmin.platform.infrastructure.adapter.out.persistence.entity;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("seguridad.rol_permisos")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class RolPermisoEntity extends BaseAuditEntity {

    @Column("id_rol")
    private Long idRol;

    @Column("id_permiso")
    private Long idPermiso;
}
