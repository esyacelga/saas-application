package com.gymadmin.core.infrastructure.adapter.out.persistence.entity;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Table("core.membresias")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class MembresiaEntity extends BaseAuditEntity {

    @Id
    private Long id;

    @Column("id_compania")
    private Long idCompania;

    @Column("id_sucursal")
    private Long idSucursal;

    @Column("id_cliente")
    private Long idCliente;

    @Column("id_tipo_membresia")
    private Long idTipoMembresia;

    @Column("id_metodo_pago")
    private Long idMetodoPago;

    @Column("id_usuario_registro")
    private Long idUsuarioRegistro;

    @Column("fecha_inicio")
    private LocalDate fechaInicio;

    @Column("fecha_fin")
    private LocalDate fechaFin;

    @Column("dias_acceso_total")
    private Integer diasAccesoTotal;

    @Column("precio_pagado")
    private BigDecimal precioPagado;

    @Column("descuento_aplicado")
    private BigDecimal descuentoAplicado;

    @Column("estado")
    private String estado;

    @Column("asistencias_previas")
    private Integer asistenciasPrevias;

    @Column("estado_pago")
    private String estadoPago;

    @Column("origen")
    private String origen;

    @Column("fecha_eliminacion")
    private OffsetDateTime fechaEliminacion;

    @Column("eliminado_por")
    private Integer eliminadoPor;

    @Column("motivo_eliminacion")
    private String motivoEliminacion;
}
