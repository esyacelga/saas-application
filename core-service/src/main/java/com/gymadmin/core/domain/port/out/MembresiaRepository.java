package com.gymadmin.core.domain.port.out;

import com.gymadmin.core.domain.model.Membresia;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface MembresiaRepository {

    /**
     * Historial completo de membresías del cliente, filtrado por compañía (multi-tenant safe).
     * Incluye membresías con {@code eliminado = true} (rechazadas) — la PWA las muestra con
     * badge opaco + motivo.
     */
    Flux<Membresia> findAllByIdClienteAndIdCompania(Long idCliente, Long idCompania);

    Mono<Membresia> findById(Long id);

    Mono<Membresia> findActivaByIdClienteAndIdCompania(Long idCliente, Long idCompania);

    Mono<Membresia> findPendienteVivaByIdCliente(Long idCliente, Long idCompania);

    /**
     * Busca una solicitud viva ({@code estado_pago='PENDIENTE'}, {@code eliminado=false})
     * originada por el propio cliente ({@code origen='cliente'}). Usada para prevenir que
     * el cliente PWA envíe dos solicitudes autoservicio simultáneas — no colisiona con
     * una venta staff pendiente que el cliente pueda tener en paralelo.
     */
    Mono<Membresia> findSolicitudClientePendiente(Long idCliente, Long idCompania);

    Flux<Membresia> findPendientesPorCompania(Long idCompania);

    /**
     * Cuenta las membresías {@code PENDIENTE} + {@code eliminado=false} de la compañía
     * agrupadas por {@code origen}. Retorna un mapa {@code origen -> cantidad}. Si no hay
     * pendientes para un origen, la clave no aparece en el mapa (el servicio decide el
     * default 0). Query única con {@code GROUP BY origen} — no dos queries.
     */
    Mono<Map<String, Long>> contarPendientesPorOrigen(Long idCompania);

    Mono<Membresia> findUltimaRechazadaByIdCliente(Long idCliente, Long idCompania);

    Mono<Long> countAsistenciasByIdMembresia(Long idMembresia);

    Mono<Membresia> save(Membresia membresia);

    Flux<Membresia> findActivasParaJob();
}
