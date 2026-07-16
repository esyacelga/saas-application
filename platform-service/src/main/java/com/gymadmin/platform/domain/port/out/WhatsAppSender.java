package com.gymadmin.platform.domain.port.out;

import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Puerto de salida para envío de mensajes por WhatsApp vía plantillas HSM pre-aprobadas
 * (Meta WhatsApp Cloud API). Análogo a {@link EmailSender}, pero para el canal {@code whatsapp}.
 *
 * <p>Los mensajes iniciados por el negocio (este caso: avisos de vencimiento) SOLO pueden usar
 * plantillas HSM aprobadas — no texto libre. El {@code templateName} identifica una plantilla
 * aprobada en Meta; {@code params} son los valores de los placeholders {@code {{1}}, {{2}}…} en
 * orden.
 */
public interface WhatsAppSender {

    /**
     * @param destinatarioE164 teléfono en formato E.164 (p. ej. {@code +593987654321}).
     * @param templateName     nombre de la plantilla HSM aprobada en Meta (p. ej. {@code recordatorio_vencimiento_suscripcion}).
     * @param idioma           código de idioma de la plantilla (p. ej. {@code es}).
     * @param params           valores de los placeholders {@code {{1}}…{{n}}} en orden.
     * @return {@link Mono#empty()} al enviar con éxito (o cuando no hay credenciales en dev/CI).
     *         Ante error del proveedor emite {@link WhatsAppSendException} clasificada como
     *         retryable / no-retryable para que la cola decida backoff vs. {@code fallido}.
     */
    Mono<Void> enviarPlantilla(String destinatarioE164, String templateName, String idioma, List<String> params);
}
