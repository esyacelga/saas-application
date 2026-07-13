package com.gymadmin.billing.domain.port.out;

import com.gymadmin.billing.domain.model.Anulacion;
import com.gymadmin.billing.domain.model.Comprobante;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface EmailNotificationPort {

    Mono<Void> enviarFactura(Comprobante comprobante, byte[] ridePdf);

    Mono<Void> enviarAlertaVencimientoCertificado(String emailDestino, String rucEmpresa,
                                                    String razonSocial, LocalDate fechaVencimiento);

    /**
     * G3 · Notifica al usuario solicitante que su anulación fue aprobada. Si
     * el flujo es B (con NC) el receptor de la factura recibe una notificación
     * separada vía {@link #enviarNotaCreditoAceptacion}.
     * <p>
     * Toda falla debe capturarse y loguearse — nunca romper el flujo funcional.
     */
    Mono<Void> enviarSolicitudAprobada(Anulacion anulacion, Comprobante comprobanteOriginal);

    /**
     * G3 · Notifica al solicitante que su anulación fue rechazada. La
     * observación explica la razón.
     */
    Mono<Void> enviarSolicitudRechazada(Anulacion anulacion, Comprobante comprobanteOriginal);

    /**
     * G3 · Notifica al receptor de la factura que se emitió una nota de
     * crédito a su favor. Según normativa SRI el receptor cuenta con 5 días
     * hábiles para aceptar la NC en su portal.
     */
    Mono<Void> enviarNotaCreditoAceptacion(Comprobante notaCredito, Comprobante facturaOriginal);
}
