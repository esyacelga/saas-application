package com.gymadmin.core.domain.model;

public record ClienteListItem(
        Long id,
        String nombre,
        String ci,
        String telefono,
        String estado,
        String fotoUrl,
        String sexo,
        MembresiaResumen membresiaActiva
) {
    public record MembresiaResumen(
            Long id,
            String tipo,
            String modoControl,
            String fechaFin,
            int diasRestantes,
            Integer accesosRestantes
    ) {}
}
