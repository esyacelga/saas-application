package com.gymadmin.core.infrastructure.adapter.out.persistence.entity;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

@Table("core.tipos_membresia")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class TipoMembresiaEntity extends BaseAuditEntity {

    @Id
    private Long id;

    @Column("id_compania")
    private Long idCompania;

    @Column("id_sucursal")
    private Long idSucursal;

    @Column("nombre")
    private String nombre;

    @Column("modo_control")
    private String modoControl;

    @Column("duracion_tipo")
    private String duracionTipo;

    @Column("duracion_valor")
    private Integer duracionValor;

    @Column("dias_acceso")
    private Integer diasAcceso;

    @Column("precio")
    private BigDecimal precio;

    @Column("activo")
    private Boolean activo;
}
