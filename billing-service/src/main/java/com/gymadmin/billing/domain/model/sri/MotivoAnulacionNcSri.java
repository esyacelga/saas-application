package com.gymadmin.billing.domain.model.sri;

/**
 * Catálogo SRI de motivos de anulación de nota de crédito
 * (tabla {@code sri.motivos_anulacion_nc}). La PK es {@code id} (identity),
 * pero el {@code codigo} funcional es único.
 * Ejemplos: DEVOLUCION, DESCUENTO, ANULACION, ERROR_PRECIO, ERROR_CALIDAD.
 */
public record MotivoAnulacionNcSri(
        Integer id,
        String codigo,
        String descripcion
) {}
