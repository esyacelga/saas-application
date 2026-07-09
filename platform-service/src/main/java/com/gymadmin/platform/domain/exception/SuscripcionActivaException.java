package com.gymadmin.platform.domain.exception;

/**
 * REQ-SAAS-001: se lanza al intentar crear una nueva suscripción (activar Trial,
 * upgrade, etc.) cuando el tenant ya tiene una suscripción ACTIVA o EN_GRACIA
 * que no admite ese cambio.
 */
public class SuscripcionActivaException extends RuntimeException {

    public SuscripcionActivaException(String message) {
        super(message);
    }
}
