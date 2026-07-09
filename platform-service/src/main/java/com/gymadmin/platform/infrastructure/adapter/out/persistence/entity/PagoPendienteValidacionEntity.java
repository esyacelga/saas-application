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
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * REQ-SAAS-001 (RN-08) — script 05_create_table_tenant_pagos_pendientes_validacion.sql.
 * <p>
 * Nota sobre auditoría: la tabla no comparte el patrón exacto de {@link BaseAuditEntity}
 * (no tiene {@code eliminado}=true por default en Liquibase auditing; solo tiene
 * {@code eliminado} + {@code creacion_fecha/usuario} + {@code modifica_fecha/usuario}
 * SIN {@code @CreatedDate}/{@code @LastModifiedDate} de Spring). Se extiende igual para
 * mantener consistencia; los campos automáticos los pobla R2dbcConfig via el auditor
 * reactivo — el patrón ya funciona para el resto de las tablas.
 */
@Table("tenant.pagos_pendientes_validacion")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PagoPendienteValidacionEntity extends BaseAuditEntity {

    @Id
    private Long id;

    @Column("id_compania")
    private Long idCompania;

    @Column("id_plan_destino")
    private Long idPlanDestino;

    @Column("monto")
    private BigDecimal monto;

    @Column("moneda")
    private String moneda;

    @Column("fecha_reporte")
    private OffsetDateTime fechaReporte;

    @Column("fecha_transferencia")
    private LocalDate fechaTransferencia;

    @Column("comprobante_url")
    private String comprobanteUrl;

    @Column("comprobante_hash")
    private String comprobanteHash;

    @Column("banco_origen")
    private String bancoOrigen;

    @Column("referencia")
    private String referencia;

    @Column("hash_idempotencia")
    private String hashIdempotencia;

    /** Valores permitidos (minúsculas, decisión D4): pendiente / aprobado / rechazado. */
    @Column("estado")
    private String estado;

    @Column("motivo_rechazo")
    private String motivoRechazo;

    @Column("aprobado_por")
    private Long aprobadoPor;

    @Column("fecha_aprobacion")
    private OffsetDateTime fechaAprobacion;

    @Column("activacion_programada")
    private Boolean activacionProgramada;

    @Column("factura_emitida_id")
    private Long facturaEmitidaId;
}
