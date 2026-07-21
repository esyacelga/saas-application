package com.gymadmin.platform.domain.model;

/**
 * Value object del listado de compañías: agrupa una {@link Compania} con los
 * datos de su suscripción activa ({@link CompaniaPlan} + nombre legible del
 * {@link Plan}). {@code planActivo} es {@code null} cuando la compañía no tiene
 * suscripción vigente.
 */
public record CompaniaConPlan(Compania compania, PlanActivo planActivo) {

    /**
     * Datos mínimos de la suscripción activa que consume el listado; el nombre
     * proviene de {@link Plan#getNombre()}, el estado y la fecha de fin de
     * {@link CompaniaPlan}.
     */
    public record PlanActivo(String nombre, CompaniaPlan.Estado estado, java.time.LocalDate fechaFin) {}

    public static CompaniaConPlan sinPlan(Compania compania) {
        return new CompaniaConPlan(compania, null);
    }
}
