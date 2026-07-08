package com.gymadmin.finance.infrastructure.adapter.out.persistence.entity;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table("finanzas.ingresos")
public class IngresoEntity extends BaseAuditEntity {

    @Id
    @Column("id")
    private Integer id;

    @Column("id_compania")
    private Integer idCompania;

    @Column("id_sucursal")
    private Integer idSucursal;

    @Column("id_categoria")
    private Integer idCategoria;

    @Column("id_membresia")
    private Integer idMembresia;

    @Column("id_venta")
    private Integer idVenta;

    @Column("monto")
    private BigDecimal monto;

    @Column("descripcion")
    private String descripcion;

    @Column("fecha")
    private LocalDate fecha;

    @Column("id_usuario_registro")
    private Integer idUsuarioRegistro;
}
