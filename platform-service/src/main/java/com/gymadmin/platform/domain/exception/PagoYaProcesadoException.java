package com.gymadmin.platform.domain.exception;

/**
 * REQ-SAAS-001 (RN-08): se lanza cuando el UPDATE atómico de aprobación o rechazo
 * de un pago pendiente afecta 0 filas — significa que otro operador root ya lo
 * procesó primero (protección contra double-processing concurrente).
 */
public class PagoYaProcesadoException extends RuntimeException {

    private final Long idPago;

    public PagoYaProcesadoException(String message, Long idPago) {
        super(message);
        this.idPago = idPago;
    }

    public Long getIdPago() { return idPago; }
}
