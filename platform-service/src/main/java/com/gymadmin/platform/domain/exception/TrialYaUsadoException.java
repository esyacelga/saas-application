package com.gymadmin.platform.domain.exception;

/**
 * REQ-SAAS-001 (RN-01): el Trial es único e irrevocable por tenant. Esta excepción
 * se lanza cuando se intenta activar Trial en una compañía cuyo {@code trial_usado}
 * ya es TRUE.
 */
public class TrialYaUsadoException extends RuntimeException {

    public TrialYaUsadoException(String message) {
        super(message);
    }
}
