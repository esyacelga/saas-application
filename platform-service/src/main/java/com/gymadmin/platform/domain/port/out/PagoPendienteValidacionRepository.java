package com.gymadmin.platform.domain.port.out;

import com.gymadmin.platform.domain.model.PagoPendienteValidacion;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * REQ-SAAS-001 (RN-08): puerto out para el buzón de pagos pendientes de validación.
 */
public interface PagoPendienteValidacionRepository {

    Mono<PagoPendienteValidacion> save(PagoPendienteValidacion pago);

    Mono<PagoPendienteValidacion> findById(Long id);

    /**
     * Devuelve el pago pendiente/aprobado con ese hash (invariante RN-08).
     * Emite {@code Mono.empty()} si no existe uno vigente.
     */
    Mono<PagoPendienteValidacion> findByHashIdempotencia(String hash);

    /**
     * Listado paginado para la bandeja de root/soporte (HU-05).
     * Ordena por {@code fecha_reporte DESC}. Si {@code estado} es null, no filtra.
     */
    Flux<PagoPendienteValidacion> listar(String estado, int offset, int limit);

    Mono<Long> contar(String estado);

    /**
     * UPDATE atómico: transiciona {@code estado} de PENDIENTE a APROBADO.
     * Retorna la cantidad de filas afectadas (0 si otro operador ya lo procesó).
     */
    Mono<Long> marcarAprobado(Long idPago, Long idUsuarioRoot, Instant fechaAprobacion);

    /**
     * UPDATE atómico: transiciona {@code estado} de PENDIENTE a RECHAZADO.
     * Retorna la cantidad de filas afectadas.
     */
    Mono<Long> marcarRechazado(Long idPago, Long idUsuarioRoot, String motivo, Instant fechaRechazo);

    /**
     * Devuelve el pago RECHAZADO más reciente de una compañía (por {@code fecha_aprobacion DESC}).
     * Usado por el renderer de la notificación {@code PAGO_RECHAZADO} para obtener
     * {@code motivo_rechazo} y {@code fecha_reporte} sin persistir esos datos en la propia notificación.
     */
    Mono<PagoPendienteValidacion> findUltimoRechazadoByCompania(Long idCompania);
}
