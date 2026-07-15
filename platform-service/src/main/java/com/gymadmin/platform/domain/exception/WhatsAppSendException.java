package com.gymadmin.platform.domain.exception;

/**
 * Error al enviar un mensaje por WhatsApp (Meta Cloud API). Clasifica el fallo como
 * <b>retryable</b> o <b>no-retryable</b> para que la cola de notificaciones decida entre
 * reintentar con backoff o marcar {@code fallido} de inmediato (R5).
 *
 * <ul>
 *   <li><b>retryable</b>: 429 (rate limit), 5xx, timeouts de conexión/lectura → backoff.</li>
 *   <li><b>no-retryable</b>: 4xx de negocio (p. ej. {@code 131047} sin sesión/consentimiento,
 *       {@code 132000} plantilla no aprobada) → {@code fallido} sin reintentar.</li>
 * </ul>
 *
 * <p>{@link #getMetaErrorCode()} guarda el {@code error.code} de Meta (si vino) para loggearlo
 * en {@code ultimo_error}.
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
