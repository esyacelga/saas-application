package com.gymadmin.core.infrastructure.adapter.in.web.dto;

import com.gymadmin.core.domain.model.ClienteDetalle;

import java.math.BigDecimal;

// Jackson SNAKE_CASE serializa: idPersona→id_persona, pesoKg→peso_kg, alturaCm→altura_cm,
// fechaIngreso→fecha_ingreso, codigoCarnet→codigo_carnet, membresiaActiva→membresia_activa,
// modoControl→modo_control, fechaInicio→fecha_inicio, fechaFin→fecha_fin,
// diasRestantes→dias_restantes, fotoUrl→foto_url, fechaNacimiento→fecha_nacimiento
public record ClienteDetalleResponse(
        Long id,
        Long idPersona,
        PersonaDto persona,
        BigDecimal pesoKg,
        BigDecimal alturaCm,
        String objetivos,
        String lesiones,
        String estado,
        String fechaIngreso,
        String codigoCarnet,
        String sexo,
        MembresiaActivaDto membresiaActiva
) {
    public record PersonaDto(String ci, String nombre, String telefono, String correo, String fotoUrl, String fechaNacimiento) {}

    public record MembresiaActivaDto(
            Long id,
            String tipo,
            String modoControl,
            String fechaInicio,
            String fechaFin,
            int diasRestantes,
            String estado
    ) {}

    public static ClienteDetalleResponse from(ClienteDetalle d) {
        PersonaDto persona = new PersonaDto(
                d.persona().ci(), d.persona().nombre(),
                d.persona().telefono(), d.persona().correo(),
                d.persona().fotoUrl(),
                d.persona().fechaNacimiento() != null ? d.persona().fechaNacimiento().toString() : null
        );
        MembresiaActivaDto mem = null;
        if (d.membresiaActiva() != null) {
            var m = d.membresiaActiva();
            mem = new MembresiaActivaDto(m.id(), m.tipo(), m.modoControl(),
                    m.fechaInicio(), m.fechaFin(), m.diasRestantes(), m.estado());
        }
        return new ClienteDetalleResponse(
                d.id(), d.idPersona(), persona, d.pesoKg(), d.alturaCm(),
                d.objetivos(), d.lesiones(), d.estado(),
                d.fechaIngreso(), d.codigoCarnet(), d.sexo(), mem
        );
    }
}
