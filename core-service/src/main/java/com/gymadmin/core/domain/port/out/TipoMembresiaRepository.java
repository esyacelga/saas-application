package com.gymadmin.core.domain.port.out;

import com.gymadmin.core.domain.model.TipoMembresia;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TipoMembresiaRepository {

    Flux<TipoMembresia> findActivosByIdCompania(Long idCompania);

    Mono<TipoMembresia> findById(Long id);

    Mono<TipoMembresia> findByNombreAndIdCompania(String nombre, Long idCompania);

    Mono<Boolean> existeMembresiaActivaDeEsteTipo(Long idTipoMembresia);

    Mono<TipoMembresia> save(TipoMembresia tipo);
}
