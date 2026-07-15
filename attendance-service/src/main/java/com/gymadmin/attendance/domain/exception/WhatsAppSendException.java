package com.gymadmin.attendance.domain.exception;

/**
 * Error al enviar un mensaje por WhatsApp (Meta Cloud API). Clasifica el fallo como
 * <b>retryable</b> o <b>no-retryable</b> (R5). En attendance el {@code MensajeriaJob} no reencola
 * con backoff (a diferencia de la cola del dueño en platform-service): un fallo marca
 * {@code fallido} en {@code mensajes_log} y queda disponible para reenvío manual. La clasificación
 * se conserva para logging/observabilidad y para futura paridad con la cola.
 *
 * <p>{@link #getMetaErrorCode()} guarda el {@code error.code} de Meta (si vino) para loggearlo.
 */
public class WhatsAppSendException extends RuntimeException {

    private final boolean retryable;
    private final Integer metaErrorCode;

    public WhatsAppSendException(String message, boolean retryable, Integer metaErrorCode) {
        super(message);
        this.retryable = retryable;
        this.metaErrorCode = metaErrorCode;
    }

    public WhatsAppSendException(String message, boolean retryable, Integer metaErrorCode, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
        this.metaErrorCode = metaErrorCode;
    }

    public boolean isRetryable() {
        return retryable;
    }

    /** El {@code error.code} devuelto por Meta, o {@code null} si el fallo fue de transporte/timeout. */
    public Integer getMetaErrorCode() {
        return metaErrorCode;
    }
}
