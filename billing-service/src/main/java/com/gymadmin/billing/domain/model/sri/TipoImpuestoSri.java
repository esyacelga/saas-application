package com.gymadmin.billing.domain.model.sri;

/**
 * Catálogo SRI de tipos de impuesto (tabla {@code sri.tipos_impuesto}).
 * Ejemplos: 2 IVA, 3 ICE, 5 IRBPNR.
 */
public record TipoImpuestoSri(
        String codigo,
        String nombre
) {}
