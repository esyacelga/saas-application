package com.gymadmin.core.infrastructure.exception;

/**
 * Excepción portadora de un {@link ErrorCode} explícito. El {@link com.gymadmin.core.infrastructure.config.GlobalExceptionHandler}
 * la detecta con precedencia sobre las excepciones genéricas ({@link ConflictException},
 * {@link NotFoundException}, {@link BusinessException}) para emitir el {@code codigo}
 * de negocio correcto (p. ej. {@code solicitud_ya_existe} en vez de {@code conflicto}).
 *
 * <p>Uso: lanzar cuando el negocio requiere un código estable consumido por frontends
 * (contrato RFC 7807 + {@code codigo}). Ver {@code docs/gym-administrator/architecture/error-contract.md}.
 */
public class CodedException extends RuntimeException {

    private final ErrorCode errorCode;

    public CodedException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
