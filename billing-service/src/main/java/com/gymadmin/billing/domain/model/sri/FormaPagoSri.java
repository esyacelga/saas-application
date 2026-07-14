package com.gymadmin.billing.domain.model.sri;

/**
 * Catálogo SRI de formas de pago (tabla {@code sri.formas_pago}).
 * Ejemplos: 01 SIN_UTILIZACION_SISTEMA_FINANCIERO, 19 TARJETA_CREDITO,
 * 20 OTROS_CON_UTILIZACION_SISTEMA_FINANCIERO.
 * <p>
 * {@code bancarizada} indica si el medio utiliza el sistema financiero. G10 lo
 * usa para exigir que el excedente sobre USD 500 se pague por un medio bancarizado.
 */
public record FormaPagoSri(
        String codigo,
        String nombre,
        boolean activo,
        boolean bancarizada
) {}
