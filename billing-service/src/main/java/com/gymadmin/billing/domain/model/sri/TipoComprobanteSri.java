package com.gymadmin.billing.domain.model.sri;

/**
 * Catálogo SRI de tipos de comprobante (tabla {@code sri.tipos_comprobante}).
 * Ejemplos: 01 FACTURA, 03 LIQUIDACION_COMPRA, 04 NOTA_CREDITO, 05 NOTA_DEBITO,
 * 06 GUIA_REMISION, 07 COMPROBANTE_RETENCION.
 */
public record TipoComprobanteSri(
        String codigo,
        String nombre,
        String version,
        boolean activo
) {}
