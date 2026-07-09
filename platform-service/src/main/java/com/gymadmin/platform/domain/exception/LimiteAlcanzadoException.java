package com.gymadmin.platform.domain.exception;

import com.gymadmin.platform.domain.model.RecursoLimitable;

/**
 * REQ-SAAS-001 (RN-05): límite duro de un plan alcanzado. La levanta
 * {@code LimiteRecursoService} cuando el uso actual iguala o supera el máximo
 * permitido por el plan activo del tenant. La Sub-fase 1.4 la mapea a HTTP
 * 409 (o 422) en el {@code GlobalExceptionHandler}.
 */
public class LimiteAlcanzadoException extends RuntimeException {

    private final RecursoLimitable recurso;
    private final long actual;
    private final long maximo;
    private final String planCodigo;

    public LimiteAlcanzadoException(RecursoLimitable recurso, long actual, long maximo, String planCodigo) {
        super("Límite alcanzado para " + recurso + " (actual=" + actual
                + ", max=" + maximo + ", plan=" + planCodigo + ")");
        this.recurso = recurso;
        this.actual = actual;
        this.maximo = maximo;
        this.planCodigo = planCodigo;
    }

    public RecursoLimitable getRecurso() { return recurso; }

    public long getActual() { return actual; }

    public long getMaximo() { return maximo; }

    public String getPlanCodigo() { return planCodigo; }
}
