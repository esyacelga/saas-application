package com.gymadmin.platform.domain.port.out;

import com.gymadmin.platform.domain.model.ActividadPlataforma;
import com.gymadmin.platform.domain.port.in.ActividadPlataformaUseCase;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ActividadPlataformaRepository {

    Mono<Void> save(ActividadPlataforma actividad);

    Flux<ActividadPlataforma> findAll(ActividadPlataformaUseCase.ListarQuery query);

    Mono<Long> count(ActividadPlataformaUseCase.ListarQuery query);
}