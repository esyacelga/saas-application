package com.gymadmin.platform.domain.exception;

/**
 * REQ-SAAS-001 (sección 5bis — máquina de estados de CompaniaPlan.Estado):
 * se lanza cuando una transición prohibida se intenta persistir.
 * <p>
 * Transiciones prohibidas:
 * <ul>
 *   <li>CANCELADO → cualquier otro (estado terminal)</li>
 *   <li>REEMPLAZADA → cualquier otro (estado terminal)</li>
 *   <li>Cualquier estado → PROGRAMADO (PROGRAMADO solo puede provenir de INSERT)</li>
 * </ul>
 * <p>
 * La excepción es de dominio (RuntimeException) — el
 * {@code GlobalExceptionHandler} de {@code infrastructure/exception} decide
 * cómo mapearla a HTTP.
 */
public class EstadoInvalidoException extends RuntimeException {

    public EstadoInvalidoException(String message) {
        super(message);
    }
}
