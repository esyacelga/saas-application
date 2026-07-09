package com.gymadmin.platform.domain.exception;

/**
 * REQ-SAAS-001 (RN-09): se lanza cuando el owner intenta cancelar su suscripción
 * pero está en Free permanente (o no tiene ninguna suscripción activa cancelable).
 */
public class SinSuscripcionCancelableException extends RuntimeException {

    public SinSuscripcionCancelableException(String message) {
        super(message);
    }
}
