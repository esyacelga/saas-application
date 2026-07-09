package com.gymadmin.billing.infrastructure.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;

@Table("facturacion.comprobantes_detalle")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComprobanteDetalleEntity {

    @Id
    private Long id;

    @Column("id_comprobante")
    private Long idComprobante;

    @Column("codigo_principal")
    private String codigoPrincipal;

    @Column("codigo_auxiliar")
    private String codigoAuxiliar;

    @Column("descripcion")
    private String descripcion;

    @Column("cantidad")
    private BigDecimal cantidad;

    @Column("precio_unitario")
    private BigDecimal precioUnitario;

    @Column("descuento")
    private BigDecimal descuento;

    @Column("precio_total_sin_impuesto")
    private BigDecimal precioTotalSinImpuesto;

    @Column("orden")
    private Integer orden;
}
