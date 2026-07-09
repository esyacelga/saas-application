package com.gymadmin.billing.domain.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ComprobanteDetalle {

    private Long id;
    private Long idComprobante;
    private String codigoPrincipal;
    private String codigoAuxiliar;
    private String descripcion;
    private BigDecimal cantidad;
    private BigDecimal precioUnitario;
    private BigDecimal descuento;
    private BigDecimal precioTotalSinImpuesto;
    private Integer orden;
}
