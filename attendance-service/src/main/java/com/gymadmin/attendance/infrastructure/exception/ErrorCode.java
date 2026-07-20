package com.gymadmin.attendance.infrastructure.exception;

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
 *
 * <p><b>Congelados (hallazgo #3):</b> el PWA de socios (`gym-member-pwa`) hace
 * branching de UI sobre valores exactos de {@code codigo} que emite
 * {@code ConflictException} ({@code ya_registrado_hoy}, {@code sin_membresia},
 * {@code membresia_expirada}, {@code accesos_agotados}, {@code congelado},
 * {@code ultima_plantilla}). Estos NO se traducen aquí: el handler usa el
 * {@code getCodigo()} de la excepción tal cual. Renombrarlos rompe el check-in
 * del socio en producción.
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
    ERROR_INTERNO(HttpStatus.INTERNAL_SERVER_ERROR, "error_interno"),

    // --- Específicos de attendance-service ---
    RECURSO_NO_DISPONIBLE(HttpStatus.GONE, "recurso_no_disponible");

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
