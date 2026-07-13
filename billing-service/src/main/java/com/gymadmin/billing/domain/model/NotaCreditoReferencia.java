package com.gymadmin.billing.domain.model;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Referencia de una nota de crédito a la factura original que corrige
 * ({@code facturacion.notas_credito_referencias}).
 * <p>
 * {@code idComprobante} apunta al comprobante NC (tipo {@code "04"}), no a la
 * factura original — esa se identifica vía
 * {@link Comprobante#getIdComprobanteRef()} + los campos denormalizados
 * {@code numDocModificado} / {@code fechaEmisionModif} de esta referencia.
 */
@Data
@Builder(toBuilder = true)
public class NotaCreditoReferencia {

    private Long id;
    private Integer idCompania;
    private Integer idSucursal;
    /** FK a {@code facturacion.comprobantes.id} de la NC. */
    private Long idComprobante;
    /** Código SRI del tipo de comprobante modificado. Hoy siempre {@code "01"} (factura). */
    private String codDocModificado;
    /** Número de la factura original en formato {@code 001-001-000000001}. */
    private String numDocModificado;
    /** Fecha de emisión de la factura original. */
    private LocalDate fechaEmisionModif;
    /** PK del motivo en {@code sri.motivos_anulacion_nc} (columna {@code id}). */
    private Integer idMotivoAnulacion;
    /** Descripción libre del motivo aportada por el usuario. */
    private String razon;
    /** Valor total del ajuste (base) que aplica esta NC. */
    private BigDecimal valorModificado;
}
