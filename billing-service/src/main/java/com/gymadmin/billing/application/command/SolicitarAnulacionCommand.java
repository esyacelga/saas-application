package com.gymadmin.billing.application.command;

/**
 * Input al caso de uso {@code AnulacionUseCase.solicitar}.
 * <p>
 * El {@code idCompania} y {@code idUsuarioSolicita} salen del JWT, nunca del
 * body. El {@code codigoMotivo} es opcional — si viene se valida contra
 * {@code sri.motivos_anulacion_nc}; si es {@code null} y el flujo pide NC
 * (G4) la aprobación fallará con 422 hasta que se provea un motivo válido.
 */
public record SolicitarAnulacionCommand(
        Long idComprobante,
        Integer idCompania,
        String motivo,
        String codigoMotivo,
        boolean generarNotaCredito,
        Integer idUsuarioSolicita
) {
}
