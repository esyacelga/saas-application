package com.gymadmin.billing.domain.port.in;

import com.gymadmin.billing.application.command.EmitirNotaCreditoCommand;
import com.gymadmin.billing.domain.model.Comprobante;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Caso de uso para notas de crédito electrónicas SRI Ecuador (tipo {@code "04"}).
 * Comparte modelo con {@link ComprobanteUseCase} porque una NC se persiste en la
 * misma tabla {@code facturacion.comprobantes}; solo cambian el
 * {@code tipoComprobante}, el {@code idComprobanteRef} y la fila adicional en
 * {@code facturacion.notas_credito_referencias}.
 */
public interface NotaCreditoUseCase {

    /**
     * Emite y transmite inmediatamente la NC al SRI (reutiliza el pipeline
     * síncrono G2). El comprobante devuelto refleja el estado final
     * ({@code AUTORIZADO}, {@code DEVUELTO}, {@code NO_AUTORIZADO}, {@code ERROR}).
     */
    Mono<Comprobante> emitirNotaCredito(EmitirNotaCreditoCommand command);

    /**
     * Devuelve la NC por ID validando pertenencia a {@code idCompania}.
     */
    Mono<Comprobante> buscarPorId(Long id, Integer idCompania);

    /**
     * Lista NC de la empresa. Solo devuelve comprobantes con {@code tipoComprobante = "04"}.
     */
    Flux<Comprobante> listar(Integer idCompania, Integer idSucursal, String estado,
                             Long idFacturaOriginal, int page, int limit);

    /**
     * Total (para paginación) de NC de la empresa con los mismos filtros.
     */
    Mono<Long> contar(Integer idCompania, Integer idSucursal, String estado, Long idFacturaOriginal);
}
