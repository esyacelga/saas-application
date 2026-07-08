package com.gymadmin.finance.infrastructure.adapter.out.persistence.entity;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table("finanzas.categorias_ingreso")
public class CategoriaIngresoEntity extends BaseAuditEntity {

    @Id
    @Column("id")
    private Integer id;

    @Column("id_compania")
    private Integer idCompania;

    @Column("id_sucursal")
    private Integer idSucursal;

    @Column("nombre")
    private String nombre;

    @Column("activo")
    private Boolean activo;
}
