package com.gymadmin.billing.domain.model;

/**
 * Forma de pago asociada a un comprobante, para el nodo {@code formasDePago} del ATS.
 * <p>
 * Un comprobante puede tener varias (pago mixto), por eso el ATS las agrupa en una
 * lista en lugar de un campo único.
 */
public record AtsPagoComprobante(
        Long idComprobante,
        String formaPago
) {}
