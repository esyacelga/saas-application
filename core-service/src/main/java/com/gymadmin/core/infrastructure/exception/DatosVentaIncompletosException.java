package com.gymadmin.core.infrastructure.exception;

import java.util.List;
import java.util.Map;

/**
 * Se lanza cuando el body de {@code POST /membresias/{id}/confirmar-pago} no trae los
 * campos requeridos para completar una venta originada por cliente ({@code origen='cliente'}
 * con {@code precio_pagado=0} placeholder). El sobre RFC 7807 lleva {@code codigo=datos_venta_incompletos}
 * y una lista {@code errores: [{campo, mensaje}]} para que el frontend pueda pintar cada campo.
 *
 * <p>Ver spec {@code docs/core-service/spec/solicitudes-membresia.md} §Endpoint modificado.
 */
public class DatosVentaIncompletosException extends CodedException {

    private final List<Map<String, String>> errores;

    public DatosVentaIncompletosException(String message, List<Map<String, String>> errores) {
        super(ErrorCode.DATOS_VENTA_INCOMPLETOS, message);
        this.errores = List.copyOf(errores);
    }

    public List<Map<String, String>> getErrores() {
        return errores;
    }
}
