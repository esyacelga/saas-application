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

@Table("tenant.compania_planes")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CompaniaPlanEntity extends BaseAuditEntity {

    @Id
    private Long id;

    @Column("id_compania")
    private Long idCompania;

    @Column("id_plan")
    private Long idPlan;

    @Column("fecha_inicio")
    private LocalDate fechaInicio;

    @Column("fecha_fin")
    private LocalDate fechaFin;

    @Column("dias_gracia")
    private Integer diasGracia;

    @Column("fecha_ultimo_pago")
    private LocalDate fechaUltimoPago;

    @Column("motivo_suspension")
    private String motivoSuspension;

    @Column("estado")
    private String estado;

    @Column("tipo_cambio")
    private String tipoCambio;

    @Column("id_compania_plan_orig")
    private Long idCompaniaPlanOrig;

    @Column("credito_monto")
    private BigDecimal creditoMonto;

}
