package com.gymadmin.billing.domain.port.out;

import com.gymadmin.billing.domain.model.Comprobante;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface EmailNotificationPort {

    Mono<Void> enviarFactura(Comprobante comprobante, byte[] ridePdf);

    Mono<Void> enviarAlertaVencimientoCertificado(String emailDestino, String rucEmpresa,
                                                    String razonSocial, LocalDate fechaVencimiento);
}
