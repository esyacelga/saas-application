package com.gymadmin.core.infrastructure.adapter.in.web.dto;

import com.gymadmin.core.domain.model.Cliente;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ClienteResponse(
        Long id,
        Long idPersona,
        BigDecimal pesoKg,
        BigDecimal alturaCm,
        String objetivos,
        String lesiones,
        String estado,
        LocalDate fechaIngreso,
        String codigoCarnet,
        String sexo
) {
    public static ClienteResponse from(Cliente c) {
        return new ClienteResponse(
                c.getId(), c.getIdPersona(),
                c.getPesoKg(), c.getAlturaCm(),
                c.getObjetivos(), c.getLesiones(),
                c.getEstado() != null ? c.getEstado().name() : null,
                c.getFechaIngreso(), c.getCodigoCarnet(),
                c.getSexo() != null ? c.getSexo().name() : null
        );
    }
}
