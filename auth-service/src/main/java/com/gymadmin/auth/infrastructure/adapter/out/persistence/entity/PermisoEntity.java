package com.gymadmin.auth.infrastructure.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("seguridad.permisos")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PermisoEntity extends BaseAuditEntity {

    @Id
    private Integer id;

    @Column("id_compania")
    private Integer idCompania;

    @Column("id_sucursal")
    private Integer idSucursal;

    @Column("nombre")
    private String nombre;

    @Column("descripcion")
    private String descripcion;

    @Column("modulo")
    private String modulo;
}
