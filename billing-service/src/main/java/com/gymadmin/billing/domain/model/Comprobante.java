package com.gymadmin.billing.domain.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Data
@Builder
public class Comprobante {

    private Long id;
    private Integer idCompania;
    private Integer idSucursal;
    private String tipoComprobante;
    private String claveAcceso;
    private String numeroAutorizacion;
    private String codEstablecimiento;
    private String codPuntoEmision;
    private String secuencial;
    private LocalDate fechaEmision;
    private String ambiente;
    private String tipoIdReceptor;
    private String idReceptor;
    private String razonSocialReceptor;
    private String emailReceptor;
    private String direccionReceptor;
    private String telefonoReceptor;
    private BigDecimal subtotalSinImpuesto;
    private BigDecimal subtotalIva0;
    private BigDecimal subtotalNoObjetoIva;
    private BigDecimal subtotalExentoIva;
    private BigDecimal totalDescuento;
    private BigDecimal totalIce;
    private BigDecimal totalIva;
    private BigDecimal propina;
    private BigDecimal total;
    private String moneda;
    private Integer idMembresia;
    private Integer idVenta;
    private Long idComprobanteRef;
    private String estado;
    private OffsetDateTime fechaAutorizacion;
    private String xmlFirmadoPath;
    private String xmlAutorizadoPath;
    private String ridePdfPath;
    private Integer idUsuarioRegistro;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
