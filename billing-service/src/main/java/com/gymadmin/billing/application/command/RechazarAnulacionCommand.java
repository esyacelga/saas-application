package com.gymadmin.billing.application.command;

/**
 * Input al caso de uso {@code AnulacionUseCase.rechazar}. La observación es
 * obligatoria en el rechazo (justificar por auditoría interna).
 */
public record RechazarAnulacionCommand(
        Long idAnulacion,
        Integer idCompania,
        Integer idUsuarioAprueba,
        String observacion
) {
}
