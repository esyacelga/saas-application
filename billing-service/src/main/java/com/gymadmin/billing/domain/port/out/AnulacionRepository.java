package com.gymadmin.billing.domain.port.out;

import com.gymadmin.billing.domain.model.Anulacion;
import com.gymadmin.billing.domain.model.EstadoAnulacion;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

/**
 * Puerto persistente para {@code facturacion.anulaciones}. Toda operación
 * de lectura debe filtrar por {@code idCompania} — el caller no debe confiar
 * únicamente en las FKs porque la tabla no exige un scope por tenant en la BD.
 */
public interface AnulacionRepository {

    /** Inserta la solicitud (estado inicial normalmente {@code SOLICITADA}). */
    Mono<Anulacion> save(Anulacion anulacion);

    /** Búsqueda por PK sin filtro de tenant (el caller aplica la validación). */
    Mono<Anulacion> findById(Long id);

    /** Historial por comprobante limitado a la compañía dueña. */
    Flux<Anulacion> findByIdComprobante(Long idComprobante, Integer idCompania);

    /**
     * Listado paginado. Todos los filtros excepto {@code idCompania} son
     * opcionales — {@code null} significa "no filtrar por ese campo".
     */
    Flux<Anulacion> findByEmpresa(Integer idCompania, Integer idSucursal, EstadoAnulacion estado,
                                  Long idComprobante, int offset, int limit);

    /** Total para paginación con los mismos filtros. */
    Mono<Long> countByEmpresa(Integer idCompania, Integer idSucursal, EstadoAnulacion estado, Long idComprobante);

    /**
     * Actualiza estado + campos de resolución en una sola operación.
     * Devuelve la fila resultante.
     */
    Mono<Anulacion> updateEstado(Long id,
                                 EstadoAnulacion nuevoEstado,
                                 Integer idUsuarioAprueba,
                                 OffsetDateTime fechaResolucion,
                                 String observacionResolucion,
                                 Long idComprobanteNc);
}
