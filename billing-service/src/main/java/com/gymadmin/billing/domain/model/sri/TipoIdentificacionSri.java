package com.gymadmin.billing.domain.model.sri;

/**
 * Catálogo SRI de tipos de identificación del comprador
 * (tabla {@code sri.tipos_identificacion_comprador}).
 * Ejemplos: 04 RUC, 05 CEDULA, 06 PASAPORTE, 07 CONSUMIDOR_FINAL, 08 ID_EXTERIOR.
 */
public record TipoIdentificacionSri(
        String codigo,
        String nombre
) {}
