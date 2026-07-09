package com.gymadmin.billing.infrastructure.adapter.in.web.dto;

import com.gymadmin.billing.domain.model.Comprobante;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record ComprobanteResponse(
        Long id,
        Integer idCompania,
        Integer idSucursal,
        String tipoComprobante,
        String claveAcceso,
        String numeroAutorizacion,
        String codEstablecimiento,
        String codPuntoEmision,
        String secuencial,
        LocalDate fechaEmision,
        String ambiente,
        String tipoIdReceptor,
        String idReceptor,
        String razonSocialReceptor,
        String emailReceptor,
        BigDecimal subtotalSinImpuesto,
        BigDecimal totalDescuento,
        BigDecimal totalIva,
        BigDecimal total,
        String moneda,
        String estado,
        OffsetDateTime fechaAutorizacion,
        String xmlFirmadoPath,
        String xmlAutorizadoPath,
        String ridePdfPath,
        OffsetDateTime createdAt
) {
    public static ComprobanteResponse from(Comprobante c) {
        return new ComprobanteResponse(
                c.getId(),
                c.getIdCompania(),
                c.getIdSucursal(),
                c.getTipoComprobante(),
                c.getClaveAcceso(),
                c.getNumeroAutorizacion(),
                c.getCodEstablecimiento(),
                c.getCodPuntoEmision(),
                c.getSecuencial(),
                c.getFechaEmision(),
                c.getAmbiente(),
                c.getTipoIdReceptor(),
                c.getIdReceptor(),
                c.getRazonSocialReceptor(),
                c.getEmailReceptor(),
                c.getSubtotalSinImpuesto(),
                c.getTotalDescuento(),
                c.getTotalIva(),
                c.getTotal(),
                c.getMoneda(),
                c.getEstado(),
                c.getFechaAutorizacion(),
                c.getXmlFirmadoPath(),
                c.getXmlAutorizadoPath(),
                c.getRidePdfPath(),
                c.getCreatedAt()
        );
    }
}
