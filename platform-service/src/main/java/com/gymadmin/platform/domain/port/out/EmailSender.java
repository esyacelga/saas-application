package com.gymadmin.platform.domain.port.out;

import reactor.core.publisher.Mono;

/**
 * REQ-SAAS-001 (Sub-fase 1.5): puerto de salida para envío de emails.
 * El HTML y el texto plano se envían siempre en un mismo multipart/alternative.
 */
public interface EmailSender {

    Mono<Void> enviar(String to, String subject, String htmlBody, String textBody);
}
