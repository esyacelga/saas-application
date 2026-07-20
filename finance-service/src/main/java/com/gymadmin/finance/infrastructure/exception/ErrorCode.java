package com.gymadmin.finance.infrastructure.exception;

import org.springframework.http.HttpStatus;

/**
 * Catálogo de códigos de error legibles por máquina (contrato estandarizado
 * RFC 7807 + {@code codigo}). Cada valor asocia un {@code codigo} snake_case
 * estable — que el frontend mapea a mensajes descriptivos/i18n — con su HTTP
 * status por defecto.
 *
 * <p>Ver {@code docs/gym-administrator/architecture/error-contract.md}.
 * El {@code codigo} es la clave del contrato: es estable y no debe renombrarse
 * sin coordinar con los frontends que lo consumen.
 */
public enum ErrorCode {

    // --- Comunes (todos los servicios) ---
    NO_AUTENTICADO(HttpStatus.UNAUTHORIZED, "no_autenticado"),
    ACCESO_DENEGADO(HttpStatus.FORBIDDEN, "acceso_denegado"),
    RECURSO_NO_ENCONTRADO(HttpStatus.NOT_FOUND, "recurso_no_encontrado"),
    CONFLICTO(HttpStatus.CONFLICT, "conflicto"),
    DATOS_DUPLICADOS(HttpStatus.CONFLICT, "datos_duplicados"),
    REFERENCIA_INVALIDA(HttpStatus.CONFLICT, "referencia_invalida"),
    CAMPO_REQUERIDO(HttpStatus.CONFLICT, "campo_requerido"),
    REGLA_NEGOCIO(HttpStatus.UNPROCESSABLE_ENTITY, "regla_negocio"),
    VALIDACION(HttpStatus.BAD_REQUEST, "validacion"),
    DEMASIADAS_SOLICITUDES(HttpStatus.TOO_MANY_REQUESTS, "demasiadas_solicitudes"),
    ERROR_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR, "error_interno");

    private final HttpStatus status;
    private final String codigo;

    ErrorCode(HttpStatus status, String codigo) {
        this.status = status;
        this.codigo = codigo;
    }

    public HttpStatus status() {
        return status;
    }

    public String codigo() {
        return codigo;
    }
}
