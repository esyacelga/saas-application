package com.gymadmin.platform.infrastructure.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("saas.caracteristicas")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CaracteristicaEntity extends BaseAuditEntity {

    @Id
    private Long id;

    @Column("codigo")
    private String codigo;

    @Column("nombre")
    private String nombre;

    @Column("modulo")
    private String modulo;

    @Column("activo")
    private Boolean activo;

}
