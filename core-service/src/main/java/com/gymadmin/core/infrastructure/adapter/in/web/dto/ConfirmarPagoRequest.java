package com.gymadmin.core.infrastructure.adapter.in.web.dto;

import com.gymadmin.core.domain.port.in.MembresiaUseCase;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Body opcional de {@code POST /api/v1/membresias/{id}/confirmar-pago}. Requerido cuando
 * la membresía es {@code origen='cliente'} (venta autoservicio sin datos de venta);
 * ignorado cuando es {@code origen='staff'} (venta directa PENDIENTE que ya trajo precio,
 * descuento y método al momento de vender).
 *
 * <p>Sin validaciones {@code @NotNull} — la validación condicional vive en
 * {@link com.gymadmin.core.application.service.MembresiaService#confirmarPago} para poder
 * emitir el sobre RFC 7807 con {@code codigo=datos_venta_incompletos} y detalle por-campo.
 */
public record ConfirmarPagoRequest(
        Long idMetodoPago,
        BigDecimal precioPagado,
        BigDecimal descuentoAplicado,
        LocalDate fechaInicio
) {
    public MembresiaUseCase.ConfirmarPagoCommand toCommand() {
        return new MembresiaUseCase.ConfirmarPagoCommand(
                idMetodoPago, precioPagado, descuentoAplicado, fechaInicio);
    }
}
