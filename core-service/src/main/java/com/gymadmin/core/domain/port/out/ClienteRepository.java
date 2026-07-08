package com.gymadmin.core.domain.port.out;

import com.gymadmin.core.domain.model.Cliente;
import com.gymadmin.core.domain.model.ClienteDetalle;
import com.gymadmin.core.domain.model.ClienteListItem;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

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
}
