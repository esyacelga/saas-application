package com.gymadmin.billing.infrastructure.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * Request body para {@code POST /api/v1/notas-credito}.
 * <p>
 * El servidor asigna el {@code secuencial} atómicamente contra
 * {@code facturacion.secuenciales} (igual que la factura desde G5); el cliente
 * no lo provee. El {@code id_compania} sale del JWT.
 */
public record EmitirNotaCreditoRequest(
        @NotNull Integer idSucursal,
        @NotNull @Size(min = 3, max = 3)
        @Pattern(regexp = "\\d{3}", message = "Debe tener exactamente 3 dígitos numéricos")
        String codEstablecimiento,
        @NotNull @Size(min = 3, max = 3)
        @Pattern(regexp = "\\d{3}", message = "Debe tener exactamente 3 dígitos numéricos")
        String codPuntoEmision,
        @NotNull @Size(min = 9, max = 9)
        @Pattern(regexp = "\\d{9}", message = "Debe tener exactamente 9 dígitos numéricos")
        String codigoNumerico,
        /** ID de la factura tipo {@code "01"} que la NC corrige. */
        @NotNull Long idFacturaOriginal,
        /** Código funcional del catálogo {@code sri.motivos_anulacion_nc}. */
        @NotBlank String codigoMotivo,
        /** Descripción libre del motivo (obligatoria en la ficha SRI). */
        @NotBlank String razon,
        /** Total del ajuste. Debe ser positivo y ≤ total de la factura original. */
        @NotNull @DecimalMin(value = "0.01", message = "Debe ser positivo") BigDecimal valorModificacion,
        /** Líneas de la NC (mismo shape que factura). */
        @NotEmpty @Valid List<DetalleItem> detalles
) {
}
