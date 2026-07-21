package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

/**
 * GYM-002: respuesta del disparo manual del recordatorio de vencimiento por WhatsApp.
 *
 * @param enviado  {@code true} si el mensaje se envió (los fallos vienen como error con {@code codigo}).
 * @param telefono teléfono E.164 al que se envió (p. ej. {@code +593987654321}).
 * @param template plantilla HSM usada según los días restantes.
 */
public record RecordatorioVencimientoResponse(boolean enviado, String telefono, String template) {
}
