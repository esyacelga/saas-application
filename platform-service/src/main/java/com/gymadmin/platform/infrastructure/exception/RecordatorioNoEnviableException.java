package com.gymadmin.platform.infrastructure.exception;

/**
 * GYM-002: el recordatorio de vencimiento manual por WhatsApp no puede enviarse por una regla
 * de negocio (sin consentimiento, teléfono no normalizable o sin suscripción activa).
 *
 * <p>Lleva su propio {@link ErrorCode} para que el {@code codigo} exacto
 * ({@code no_consentimiento}, {@code telefono_invalido}, {@code sin_suscripcion}) viaje al
 * frontend, que lo usa para discriminar el mensaje. El {@code GlobalExceptionHandler} la mapea
 * usando el {@link ErrorCode} embebido.
 */
public class RecordatorioNoEnviableException extends RuntimeException {

    private final ErrorCode errorCode;
    private final java.time.LocalDateTime fechaEnvioPrevio;

    public RecordatorioNoEnviableException(ErrorCode errorCode, String message) {
        this(errorCode, message, null);
    }

    /**
     * Variante para {@code notificacion_ya_enviada}: la fecha del envío previo viaja al frontend
     * como extensión {@code fecha_envio_previo} para que el diálogo diga "ya se envió el {fecha}"
     * antes de ofrecer el reenvío forzado.
     */
    public RecordatorioNoEnviableException(ErrorCode errorCode, String message,
                                           java.time.LocalDateTime fechaEnvioPrevio) {
        super(message);
        this.errorCode = errorCode;
        this.fechaEnvioPrevio = fechaEnvioPrevio;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /** {@code null} salvo en {@code notificacion_ya_enviada}. */
    public java.time.LocalDateTime getFechaEnvioPrevio() {
        return fechaEnvioPrevio;
    }
}
