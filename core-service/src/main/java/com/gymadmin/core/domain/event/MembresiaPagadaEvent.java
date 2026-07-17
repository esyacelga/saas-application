package com.gymadmin.core.domain.event;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Evento de dominio emitido cuando una membresía transita de PENDIENTE a PAGADO
 * (o se crea directamente en PAGADO). Publicado por {@code ConfirmarPagoMembresiaService}
 * y {@code MembresiaService.vender} via {@link org.springframework.context.ApplicationEventPublisher}.
 *
 * <p>No hay consumidores dentro de core-service en esta HU. El evento existe para
 * que la HU-C (integración con billing-service) pueda registrar un
 * {@code @EventListener} que emita factura electrónica sin refactorizar este servicio.
 *
 * <p>Convención — establece el paquete {@code domain/event/} para core-service.
 * Todos los eventos de dominio de este microservicio viven aquí.
 */
public record MembresiaPagadaEvent(
        Long idMembresia,
        Long idCliente,
        Long idCompania,
        BigDecimal montoPagado,
        LocalDate fechaConfirmacion
) {}
