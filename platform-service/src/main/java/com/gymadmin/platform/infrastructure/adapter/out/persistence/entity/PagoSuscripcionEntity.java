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

@Table("tenant.pagos_suscripcion")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PagoSuscripcionEntity extends BaseAuditEntity {

    @Id
    private Long id;

    @Column("id_compania_plan")
    private Long idCompaniaPlan;

    @Column("monto")
    private BigDecimal monto;

    @Column("fecha_pago")
    private LocalDate fechaPago;

    @Column("periodo_desde")
    private LocalDate periodoDesde;

    @Column("periodo_hasta")
    private LocalDate periodoHasta;

    @Column("metodo_pago")
    private String metodoPago;

    @Column("tipo_pago")
    private String tipoPago;

    @Column("estado")
    private String estado;

    @Column("referencia")
    private String referencia;

}
