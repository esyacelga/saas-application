package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import com.gymadmin.platform.domain.model.Caracteristica;
import com.gymadmin.platform.domain.model.Plan;

import java.math.BigDecimal;
import java.util.List;

/**
 * REQ-SAAS-001: representación pública de un plan (Free / Trial / Premium)
 * consumida por la landing y el wizard de auto-registro.
 */
public record PlanPublicoResponse(
        Long id,
        String codigo,
        String nombre,
        String descripcion,
        BigDecimal precioMensual,
        String moneda,
        Integer duracionDias,
        boolean esGratuito,
        Integer maxSucursales,
        Integer maxClientesActivos,
        Integer maxStaff,
        List<CaracteristicaDto> caracteristicas
) {
    public static PlanPublicoResponse from(Plan plan) {
        List<CaracteristicaDto> caracs = plan.getCaracteristicas() != null
                ? plan.getCaracteristicas().stream()
                    .map(PlanPublicoResponse::toDto)
                    .toList()
                : List.of();
        return new PlanPublicoResponse(
                plan.getId(),
                plan.getCodigo(),
                plan.getNombre(),
                plan.getDescripcion(),
                plan.getPrecioMensual(),
                plan.getMoneda(),
                plan.getDuracionDias(),
                plan.isEsGratuito(),
                plan.getMaxSucursales(),
                plan.getMaxClientesActivos(),
                plan.getMaxStaff(),
                caracs
        );
    }

    private static CaracteristicaDto toDto(Caracteristica c) {
        return new CaracteristicaDto(c.getId(), c.getCodigo(), c.getNombre(), c.getModulo(), c.getActivo());
    }
}
