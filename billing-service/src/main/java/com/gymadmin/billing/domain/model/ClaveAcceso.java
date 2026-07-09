package com.gymadmin.billing.domain.model;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Value object representing the 49-digit SRI Ecuador access key (clave de acceso).
 *
 * Structure:
 *   Pos  1-8:  fecha emisión (ddmmaaaa)
 *   Pos  9-10: tipo comprobante (01=factura, 04=nota crédito)
 *   Pos 11-23: RUC (13 digits)
 *   Pos    24: ambiente (1=pruebas, 2=producción)
 *   Pos 25-27: establecimiento (3 digits, zero-padded)
 *   Pos 28-30: punto de emisión (3 digits, zero-padded)
 *   Pos 31-39: secuencial (9 digits, zero-padded)
 *   Pos 40-48: código numérico (9 digits)
 *   Pos    49: dígito verificador (módulo 11)
 */
public final class ClaveAcceso {

    private static final DateTimeFormatter FECHA_FORMATTER = DateTimeFormatter.ofPattern("ddMMyyyy");

    private final String value;

    private ClaveAcceso(String value) {
        this.value = value;
    }

    public static ClaveAcceso generar(
            LocalDate fechaEmision,
            String tipoComprobante,
            String ruc,
            String ambiente,
            String codEstablecimiento,
            String codPuntoEmision,
            String secuencial,
            String codigoNumerico) {

        String fecha = fechaEmision.format(FECHA_FORMATTER);
        String primeros48 = fecha
                + tipoComprobante
                + ruc
                + ambiente
                + codEstablecimiento
                + codPuntoEmision
                + secuencial
                + codigoNumerico;

        if (primeros48.length() != 48) {
            throw new IllegalArgumentException(
                    "Los primeros 48 caracteres de la clave de acceso tienen longitud inválida: "
                    + primeros48.length() + ". Valor: " + primeros48);
        }

        int digitoVerificador = calcularDigitoVerificador(primeros48);
        return new ClaveAcceso(primeros48 + digitoVerificador);
    }

    public static ClaveAcceso of(String value) {
        if (value == null || value.length() != 49) {
            throw new IllegalArgumentException("La clave de acceso debe tener exactamente 49 dígitos");
        }
        if (!value.matches("\\d{49}")) {
            throw new IllegalArgumentException("La clave de acceso solo puede contener dígitos numéricos");
        }
        int expectedDigit = calcularDigitoVerificador(value.substring(0, 48));
        int actualDigit = Character.getNumericValue(value.charAt(48));
        if (expectedDigit != actualDigit) {
            throw new IllegalArgumentException(
                    "Dígito verificador inválido. Esperado: " + expectedDigit + ", Recibido: " + actualDigit);
        }
        return new ClaveAcceso(value);
    }

    public String getValue() {
        return value;
    }

    /**
     * Módulo 11 algorithm for SRI Ecuador check digit calculation.
     *
     * 1. Multiply each digit (positions 1-48) by weights cycling through [2,3,4,5,6,7]
     *    starting from the rightmost digit.
     * 2. Sum all products.
     * 3. result = 11 - (sum % 11)
     * 4. if result == 11 → digit = 0
     * 5. if result == 10 → digit = 1
     * 6. otherwise → digit = result
     */
    private static int calcularDigitoVerificador(String primeros48) {
        int[] pesos = {2, 3, 4, 5, 6, 7};
        int suma = 0;
        int pesoIndex = 0;

        for (int i = primeros48.length() - 1; i >= 0; i--) {
            int digito = Character.getNumericValue(primeros48.charAt(i));
            suma += digito * pesos[pesoIndex % pesos.length];
            pesoIndex++;
        }

        int resultado = 11 - (suma % 11);
        if (resultado == 11) return 0;
        if (resultado == 10) return 1;
        return resultado;
    }

    @Override
    public String toString() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClaveAcceso other)) return false;
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
