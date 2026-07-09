package com.gymadmin.platform.infrastructure.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

@Table("saas.planes")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PlanEntity extends BaseAuditEntity {

    @Id
    private Long id;

    @Column("nombre")
    private String nombre;

    @Column("descripcion")
    private String descripcion;

    @Column("precio_mensual")
    private BigDecimal precioMensual;

    @Column("activo")
    private Boolean activo;

    // REQ-SAAS-001 — Sub-fase 1.2 (columnas del script 01_alter_saas_planes.sql).

    @Column("codigo")
    private String codigo;

    @Column("duracion_dias")
    private Integer duracionDias;

    @Column("es_gratuito")
    private Boolean esGratuito;

    @Column("plan_degradacion_id")
    private Long planDegradacionId;

    @Column("max_sucursales")
    private Integer maxSucursales;

    @Column("max_clientes_activos")
    private Integer maxClientesActivos;

    @Column("max_staff")
    private Integer maxStaff;

    @Column("moneda")
    private String moneda;

    @Column("es_legacy")
    private Boolean esLegacy;
}
