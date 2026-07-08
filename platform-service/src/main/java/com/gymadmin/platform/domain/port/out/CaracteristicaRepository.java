package com.gymadmin.platform.domain.port.out;

import com.gymadmin.platform.domain.model.Caracteristica;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CaracteristicaRepository {

    Flux<Caracteristica> findAll();

    Mono<Caracteristica> findById(Long id);

    Mono<Caracteristica> save(Caracteristica caracteristica);

    Mono<Caracteristica> findByCodigo(String codigo);
}
