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

    public RecordatorioNoEnviableException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
