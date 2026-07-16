package com.gymadmin.core.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ClienteDetalle(
        Long id,
        Long idPersona,
        Persona persona,
        BigDecimal pesoKg,
        BigDecimal alturaCm,
        String objetivos,
        String lesiones,
        String estado,
        String fechaIngreso,
        String codigoCarnet,
        String sexo,
        MembresiaActiva membresiaActiva
) {
    public record Persona(String ci, String nombre, String telefono, String correo, String fotoUrl, LocalDate fechaNacimiento) {}

    public record MembresiaActiva(
            Long id,
            String tipo,
            String modoControl,
            String fechaInicio,
            String fechaFin,
            int diasRestantes,
            String estado
    ) {}
}
