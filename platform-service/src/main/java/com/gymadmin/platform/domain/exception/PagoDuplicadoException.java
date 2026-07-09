package com.gymadmin.platform.domain.exception;

/**
 * REQ-SAAS-001 (RN-08): se lanza cuando el owner intenta reportar un pago cuyo
 * hash de idempotencia colisiona con uno ya registrado en estado PENDIENTE o
 * APROBADO.
 */
public class PagoDuplicadoException extends RuntimeException {

    private final String hash;

    public PagoDuplicadoException(String message, String hash) {
        super(message);
        this.hash = hash;
    }

    public String getHash() { return hash; }
}
