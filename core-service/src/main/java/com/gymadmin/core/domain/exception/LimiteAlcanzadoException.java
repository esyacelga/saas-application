package com.gymadmin.core.domain.exception;

/**
 * REQ-SAAS-001 (RN-05, Sub-fase 1.4): límite duro alcanzado para un recurso del
 * tenant. La levanta el flujo de creación cuando {@code platform-service}
 * responde {@code permite=false}. El {@code GlobalExceptionHandler} de
 * core-service la mapea a HTTP 403 con shape
 * {@code {codigo, recurso, actual, maximo, planActual}}.
 */
public class LimiteAlcanzadoException extends RuntimeException {

    private final String recurso;
    private final long actual;
    private final long maximo;
    private final String planCodigo;

    public LimiteAlcanzadoException(String recurso, long actual, long maximo, String planCodigo) {
        super("Límite alcanzado para " + recurso + " (actual=" + actual
                + ", max=" + maximo + ", plan=" + planCodigo + ")");
        this.recurso = recurso;
        this.actual = actual;
        this.maximo = maximo;
        this.planCodigo = planCodigo;
    }

    public String getRecurso() { return recurso; }

    public long getActual() { return actual; }

    public long getMaximo() { return maximo; }

    public String getPlanCodigo() { return planCodigo; }
}
