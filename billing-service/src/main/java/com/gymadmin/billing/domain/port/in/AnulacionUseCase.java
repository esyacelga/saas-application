package com.gymadmin.billing.domain.port.in;

import com.gymadmin.billing.application.command.AprobarAnulacionCommand;
import com.gymadmin.billing.application.command.RechazarAnulacionCommand;
import com.gymadmin.billing.application.command.SolicitarAnulacionCommand;
import com.gymadmin.billing.domain.model.Anulacion;
import com.gymadmin.billing.domain.model.EstadoAnulacion;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * G3 · Caso de uso de anulación fiscal SRI Ecuador. Cubre la creación de la
 * solicitud, la resolución (aprobar/rechazar), la confirmación manual del
 * trámite en portal (Flujo A), y las consultas asociadas.
 */
public interface AnulacionUseCase {

    /** Crea una nueva solicitud en estado {@link EstadoAnulacion#SOLICITADA}. */
    Mono<Anulacion> solicitar(SolicitarAnulacionCommand command);

    /**
     * Transiciona {@code SOLICITADA → APROBADA}. Si la solicitud pidió Flujo B
     * (con nota de crédito), dispara la emisión inmediata de la NC vía G4 y
     * transiciona a {@code EJECUTADA} solo si la NC llega a AUTORIZADO en el
     * mismo request; caso contrario queda en {@code APROBADA} y el scheduler
     * de G2 completará la NC en un reintento.
     */
    Mono<Anulacion> aprobar(AprobarAnulacionCommand command);

    /** Transiciona {@code SOLICITADA → RECHAZADA} guardando la observación. */
    Mono<Anulacion> rechazar(RechazarAnulacionCommand command);

    /**
     * Flujo A: el admin ejecutó el trámite en el portal SRI. Transiciona
     * {@code APROBADA → EJECUTADA} y marca el comprobante original como
     * {@code ANULADO}.
     */
    Mono<Anulacion> confirmarSri(Long idAnulacion, Integer idCompania, Integer idUsuarioConfirma);

    /** Detalle por ID validando multi-tenancy. */
    Mono<Anulacion> buscarPorId(Long id, Integer idCompania);

    /** Historial de solicitudes de un comprobante (multi-tenant). */
    Flux<Anulacion> historialPorComprobante(Long idComprobante, Integer idCompania);

    /** Listado paginado de anulaciones de la empresa. */
    Flux<Anulacion> listar(Integer idCompania, Integer idSucursal, EstadoAnulacion estado,
                           Long idComprobante, int page, int limit);

    /** Total para paginación con los mismos filtros. */
    Mono<Long> contar(Integer idCompania, Integer idSucursal, EstadoAnulacion estado, Long idComprobante);
}
