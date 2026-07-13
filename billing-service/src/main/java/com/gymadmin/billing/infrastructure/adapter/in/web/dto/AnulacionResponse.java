package com.gymadmin.billing.infrastructure.adapter.in.web.dto;

import com.gymadmin.billing.domain.model.Anulacion;

import java.time.OffsetDateTime;

/**
 * Vista pública de {@code facturacion.anulaciones}. Nunca incluye la metadata
 * interna del prefijo {@code [FLUJO_B]} / {@code [MOTIVO=...]} — se strippea
 * antes de mapear.
 */
public record AnulacionResponse(
        Long id,
        Integer idCompania,
        Integer idSucursal,
        Long idComprobante,
        String motivo,
        String estado,
        Long idComprobanteNc,
        Integer idUsuarioSolicita,
        Integer idUsuarioAprueba,
        OffsetDateTime fechaSolicitud,
        OffsetDateTime fechaResolucion,
        String observacionResolucion,
        String linkResource
) {

    public static AnulacionResponse from(Anulacion a) {
        return new AnulacionResponse(
                a.getId(),
                a.getIdCompania(),
                a.getIdSucursal(),
                a.getIdComprobante(),
                a.getMotivo(),
                a.getEstado() != null ? a.getEstado().name() : null,
                a.getIdComprobanteNc(),
                a.getIdUsuarioSolicita(),
                a.getIdUsuarioAprueba(),
                a.getFechaSolicitud(),
                a.getFechaResolucion(),
                a.getObservacionResolucion(),
                a.getId() != null ? "/api/v1/anulaciones/" + a.getId() : null
        );
    }
}
