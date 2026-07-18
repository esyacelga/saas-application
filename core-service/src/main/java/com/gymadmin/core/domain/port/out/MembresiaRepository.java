package com.gymadmin.core.domain.port.out;

import com.gymadmin.core.domain.model.Membresia;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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

    Flux<Membresia> findPendientesPorCompania(Long idCompania);

    Mono<Membresia> findUltimaRechazadaByIdCliente(Long idCliente, Long idCompania);

    Mono<Long> countAsistenciasByIdMembresia(Long idMembresia);

    Mono<Membresia> save(Membresia membresia);

    Flux<Membresia> findActivasParaJob();
}
