package com.gymadmin.billing.application.command;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Input al caso de uso de emisión de nota de crédito (tipo SRI {@code "04"}).
 * <p>
 * Reutiliza {@link EmitirFacturaCommand.DetalleFacturaItem} porque la NC lleva
 * exactamente el mismo shape de líneas que la factura original: código,
 * descripción, cantidad, precio unitario, descuento, y precio total sin
 * impuesto. No hay razón para duplicar el record.
 */
public record EmitirNotaCreditoCommand(
        Integer idCompania,
        Integer idSucursal,
        LocalDate fechaEmision,
        String codEstablecimiento,
        String codPuntoEmision,
        String codigoNumerico,
        /** ID de la factura tipo {@code "01"} que esta NC corrige. */
        Long idFacturaOriginal,
        /** Código funcional del catálogo {@code sri.motivos_anulacion_nc.codigo}. */
        String codigoMotivo,
        /** Descripción libre del motivo. */
        String razon,
        /** Total del ajuste que aplica la NC (base). No puede exceder el total de la factura original. */
        BigDecimal valorModificacion,
        List<EmitirFacturaCommand.DetalleFacturaItem> detalles,
        Integer idUsuarioRegistro
) {
}
