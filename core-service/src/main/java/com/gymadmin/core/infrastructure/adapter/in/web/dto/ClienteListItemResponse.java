package com.gymadmin.core.infrastructure.adapter.in.web.dto;

import com.gymadmin.core.domain.model.ClienteListItem;

// Jackson SNAKE_CASE: fotoUrl→foto_url
public record ClienteListItemResponse(
        Long id,
        String nombre,
        String ci,
        String telefono,
        String estado,
        String fotoUrl,
        String sexo,
        MembresiaResumen membresiaActiva
) {
    // Jackson SNAKE_CASE: modoControl→modo_control, fechaFin→fecha_fin, diasRestantes→dias_restantes, accesosRestantes→accesos_restantes
    public record MembresiaResumen(
            Long id,
            String tipo,
            String modoControl,
            String fechaFin,
            int diasRestantes,
            Integer accesosRestantes
    ) {}

    public static ClienteListItemResponse from(ClienteListItem item) {
        MembresiaResumen mem = null;
        if (item.membresiaActiva() != null) {
            var m = item.membresiaActiva();
            mem = new MembresiaResumen(m.id(), m.tipo(), m.modoControl(), m.fechaFin(), m.diasRestantes(), m.accesosRestantes());
        }
        return new ClienteListItemResponse(
                item.id(), item.nombre(), item.ci(), item.telefono(), item.estado(),
                item.fotoUrl(), item.sexo(), mem
        );
    }
}
