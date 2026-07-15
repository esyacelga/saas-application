package com.gymadmin.attendance.domain.port.out;

import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Puerto de salida para envío de mensajes por WhatsApp vía plantillas HSM pre-aprobadas
 * (Meta WhatsApp Cloud API). Duplicado pragmático (v1) del mismo puerto en platform-service — la
 * decisión de arquitectura es que cada servicio envía lo suyo sin un servicio de mensajería central.
 *
 * <p>Los mensajes iniciados por el negocio (avisos de vencimiento de membresía del socio) SOLO
 * pueden usar plantillas HSM aprobadas — no el texto libre de {@code asistencia.plantillas_mensajes}.
 * El {@code templateName} identifica una plantilla aprobada en Meta; {@code params} son los valores
 * de los placeholders {@code {{1}}, {{2}}…} en orden.
 */
public interface WhatsAppSender {

    /**
     * @param destinatarioE164 teléfono en formato E.164 (p. ej. {@code +593987654321}).
     * @param templateName     nombre de la plantilla HSM aprobada en Meta (p. ej. {@code venc_membresia_previo}).
     * @param idioma           código de idioma de la plantilla (p. ej. {@code es}).
     * @param params           valores de los placeholders {@code {{1}}…{{n}}} en orden.
     * @return {@link Mono#empty()} al enviar con éxito (o cuando no hay credenciales en dev/CI).
     *         Ante error del proveedor emite {@link com.gymadmin.attendance.domain.exception.WhatsAppSendException}.
     */
    Mono<Void> enviarPlantilla(String destinatarioE164, String templateName, String idioma, List<String> params);
}
