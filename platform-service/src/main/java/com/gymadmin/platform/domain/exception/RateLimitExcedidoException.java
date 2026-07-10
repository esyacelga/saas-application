package com.gymadmin.platform.domain.exception;

/**
 * REQ-SAAS-001 (RN-08): se lanza cuando se supera el rate limit de un bucket
 * (por ejemplo, "más de 3 reportes de pago por hora para el mismo tenant").
 * El {@code GlobalExceptionHandler} la mapea a HTTP 429.
 */
public class RateLimitExcedidoException extends RuntimeException {

    private final String ventana;
    private final int max;

    public RateLimitExcedidoException(String message, String ventana, int max) {
        super(message);
        this.ventana = ventana;
        this.max = max;
    }

    public String getVentana() { return ventana; }

    public int getMax() { return max; }
}
