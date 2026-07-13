package com.gymadmin.billing.application.command;

/**
 * Input al caso de uso {@code AnulacionUseCase.aprobar}. La observación es
 * opcional para la aprobación (usualmente vacía si el flujo es directo).
 */
public record AprobarAnulacionCommand(
        Long idAnulacion,
        Integer idCompania,
        Integer idUsuarioAprueba,
        String observacion
) {
}
