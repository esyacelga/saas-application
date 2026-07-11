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
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Table("facturacion.comprobantes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComprobanteEntity {

    @Id
    private Long id;

    @Column("id_compania")
    private Integer idCompania;

    @Column("id_sucursal")
    private Integer idSucursal;

    @Column("tipo_comprobante")
    private String tipoComprobante;

    @Column("clave_acceso")
    private String claveAcceso;

    @Column("numero_autorizacion")
    private String numeroAutorizacion;

    @Column("cod_establecimiento")
    private String codEstablecimiento;

    @Column("cod_punto_emision")
    private String codPuntoEmision;

    @Column("secuencial")
    private String secuencial;

    @Column("fecha_emision")
    private LocalDate fechaEmision;

    @Column("ambiente")
    private String ambiente;

    @Column("tipo_id_receptor")
    private String tipoIdReceptor;

    @Column("id_receptor")
    private String idReceptor;

    @Column("razon_social_receptor")
    private String razonSocialReceptor;

    @Column("email_receptor")
    private String emailReceptor;

    @Column("direccion_receptor")
    private String direccionReceptor;

    @Column("telefono_receptor")
    private String telefonoReceptor;

    @Column("subtotal_sin_impuesto")
    private BigDecimal subtotalSinImpuesto;

    @Column("subtotal_iva_0")
    private BigDecimal subtotalIva0;

    @Column("subtotal_no_objeto_iva")
    private BigDecimal subtotalNoObjetoIva;

    @Column("subtotal_exento_iva")
    private BigDecimal subtotalExentoIva;

    @Column("total_descuento")
    private BigDecimal totalDescuento;

    @Column("total_ice")
    private BigDecimal totalIce;

    @Column("total_iva")
    private BigDecimal totalIva;

    @Column("propina")
    private BigDecimal propina;

    @Column("total")
    private BigDecimal total;

    @Column("moneda")
    private String moneda;

    @Column("id_membresia")
    private Integer idMembresia;

    @Column("id_venta")
    private Integer idVenta;

    @Column("id_comprobante_ref")
    private Long idComprobanteRef;

    @Column("estado")
    private String estado;

    @Column("fecha_autorizacion")
    private OffsetDateTime fechaAutorizacion;

    @Column("xml_firmado_path")
    private String xmlFirmadoPath;

    @Column("xml_autorizado_path")
    private String xmlAutorizadoPath;

    @Column("ride_pdf_path")
    private String ridePdfPath;

    @Column("id_usuario_registro")
    private Integer idUsuarioRegistro;

    @ReadOnlyProperty
    @Column("created_at")
    private OffsetDateTime createdAt;

    @ReadOnlyProperty
    @Column("updated_at")
    private OffsetDateTime updatedAt;
}
