package com.gymadmin.billing.domain.model.sri;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Catálogo SRI de tarifas de IVA (tabla {@code sri.tarifas_iva}).
 * <p>
 * Las columnas {@code vigenteDesde} y {@code vigenteHasta} son inclusivas:
 * una fecha de emisión igual a cualquiera de las dos se considera dentro
 * de la vigencia. Cuando alguna es {@code null} significa vigencia abierta
 * hacia esa dirección (código {@code 4} IVA 15% tiene {@code vigenteHasta = null}).
 */
public record TarifaIvaSri(
        String codigo,
        String nombre,
        BigDecimal porcentaje,
        LocalDate vigenteDesde,
        LocalDate vigenteHasta
) {
    /**
     * Determina si la tarifa está vigente en la fecha indicada.
     * Las tarifas con {@code vigenteDesde}/{@code vigenteHasta} en {@code null}
     * se consideran siempre vigentes por ese extremo.
     */
    public boolean vigenteEn(LocalDate fecha) {
        if (fecha == null) {
            return false;
        }
        if (vigenteDesde != null && fecha.isBefore(vigenteDesde)) {
            return false;
        }
        if (vigenteHasta != null && fecha.isAfter(vigenteHasta)) {
            return false;
        }
        return true;
    }
}
