package com.gymadmin.billing.infrastructure.adapter.out.persistence.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

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

    @Column("id_compania")
    private Integer idCompania;

    @Column("id_sucursal")
    private Integer idSucursal;

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

    @ReadOnlyProperty
    @Column("creado_en")
    private OffsetDateTime creadoEn;
}
