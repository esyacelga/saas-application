package com.gymadmin.core.domain.port.out;

import com.gymadmin.core.domain.model.Cliente;
import com.gymadmin.core.domain.model.ClienteDetalle;
import com.gymadmin.core.domain.model.ClienteListItem;
import com.gymadmin.core.domain.model.ClientePorVencer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface ClienteRepository {

    Flux<Cliente> findByIdCompania(Long idCompania, String estado, String buscar, int offset, int limit);

    Mono<Long> countByIdCompania(Long idCompania, String estado, String buscar);

    Flux<ClienteListItem> findListItems(Long idCompania, String estado, String buscar, int offset, int limit, Boolean sinMembresia);

    Mono<Long> countListItems(Long idCompania, String estado, String buscar, Boolean sinMembresia);

    Mono<ClienteDetalle> findDetalleById(Long id, Long idCompania);

    Mono<Cliente> findById(Long id);

    Mono<Cliente> findByIdAndIdCompania(Long id, Long idCompania);

    Mono<Cliente> findByIdPersonaAndIdCompania(Long idPersona, Long idCompania);

    Mono<Cliente> save(Cliente cliente);

    Mono<Void> deleteById(Long id);

    Flux<Cliente> findByIdPersona(Long idPersona);

    Flux<Cliente> findActivosParaJob();

    /**
     * REQ-SAAS-001 (Fase 4, C3): socios de una compañía cuya membresía activa está por vencer.
     *
     * <p>{@code fechaCorte} es el "hoy" de negocio (resuelto en zona Guayaquil, C4) y se usa en la
     * query en lugar de {@code CURRENT_DATE} para evitar desfases de día. Excluye {@code congelado}
     * y {@code vencido}. El {@code modo} filtra por {@code modo_control}:
     * <ul>
     *   <li>{@code calendario}: incluye solo membresías calendario con {@code fechaFin - fechaCorte <= dias}.</li>
     *   <li>{@code accesos}: incluye solo membresías por accesos con {@code accesosRestantes <= dias}.</li>
     *   <li>{@code todos}: aplica el umbral correspondiente a cada modo.</li>
     * </ul>
     */
    Flux<ClientePorVencer> findClientesPorVencer(Long idCompania, LocalDate fechaCorte, int dias, String modo);
}
