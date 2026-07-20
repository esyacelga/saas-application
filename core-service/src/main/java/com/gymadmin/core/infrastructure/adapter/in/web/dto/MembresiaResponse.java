package com.gymadmin.core.infrastructure.adapter.in.web.dto;

import com.gymadmin.core.domain.model.Membresia;
import com.gymadmin.core.domain.port.in.MembresiaUseCase;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MembresiaResponse(
        Long id,
        Long idCliente,
        Long idTipoMembresia,
        String tipoNombre,
        String modoControl,
        LocalDate fechaInicio,
        LocalDate fechaFin,
        Integer diasAccesoTotal,
        Integer diasAccesoUsados,
        Integer diasAccesoRestantes,
        BigDecimal precioPagado,
        BigDecimal descuentoAplicado,
        BigDecimal montoPagado,
        BigDecimal saldoPendiente,
        String estado,
        String estadoPago,
        String origen,
        Boolean eliminado,
        String motivoEliminacion
) {
    /**
     * Mapping usado por los endpoints que devuelven la membresía "cruda" (venta, confirmar-pago,
     * rechazar, solicitar). Los campos derivados (nombre y modo del tipo, monto pagado/saldo,
     * accesos) se rellenan con {@code null} o desde el {@code estado_pago} — el consumidor de
     * estos endpoints (staff dashboard) obtiene el detalle enriquecido en su siguiente refresh.
     */
    public static MembresiaResponse from(Membresia m) {
        BigDecimal precio = m.getPrecioPagado() != null ? m.getPrecioPagado() : BigDecimal.ZERO;
        boolean pagado = Membresia.EstadoPago.PAGADO.equals(m.getEstadoPago());
        return new MembresiaResponse(
                m.getId(), m.getIdCliente(), m.getIdTipoMembresia(),
                null, null,
                m.getFechaInicio(), m.getFechaFin(), m.getDiasAccesoTotal(),
                null, null,
                m.getPrecioPagado(), m.getDescuentoAplicado(),
                pagado ? precio : BigDecimal.ZERO,
                pagado ? BigDecimal.ZERO : precio,
                m.getEstado() != null ? m.getEstado().name() : null,
                m.getEstadoPago() != null ? m.getEstadoPago().name() : null,
                m.getOrigen() != null ? m.getOrigen().name() : null,
                m.getEliminado(),
                m.getMotivoEliminacion() != null ? m.getMotivoEliminacion().name() : null
        );
    }

    /**
     * Mapping usado por el historial ({@code GET /clientes/{id}/membresias} y
     * {@code /clientes/me/membresias}). Rellena {@code tipoNombre}, {@code modoControl},
     * {@code montoPagado}, {@code saldoPendiente} y los contadores de accesos ya calculados
     * en la capa de aplicación.
     */
    public static MembresiaResponse from(MembresiaUseCase.MembresiaHistorialItem item) {
        Membresia m = item.membresia();
        return new MembresiaResponse(
                m.getId(), m.getIdCliente(), m.getIdTipoMembresia(),
                item.tipoNombre(), item.modoControl(),
                m.getFechaInicio(), m.getFechaFin(), m.getDiasAccesoTotal(),
                item.diasAccesoUsados(), item.diasAccesoRestantes(),
                m.getPrecioPagado(), m.getDescuentoAplicado(),
                item.montoPagado(), item.saldoPendiente(),
                m.getEstado() != null ? m.getEstado().name() : null,
                m.getEstadoPago() != null ? m.getEstadoPago().name() : null,
                m.getOrigen() != null ? m.getOrigen().name() : null,
                m.getEliminado(),
                m.getMotivoEliminacion() != null ? m.getMotivoEliminacion().name() : null
        );
    }
}
