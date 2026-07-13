package com.gymadmin.billing.domain.model.sri;

/**
 * Catálogo SRI de formas de pago (tabla {@code sri.formas_pago}).
 * Ejemplos: 01 SIN_UTILIZACION_SISTEMA_FINANCIERO, 19 TARJETA_CREDITO,
 * 20 OTROS_CON_UTILIZACION_SISTEMA_FINANCIERO.
 */
public record FormaPagoSri(
        String codigo,
        String nombre,
        boolean activo
) {}
