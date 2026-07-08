package com.gymadmin.platform.domain.port.in;

import com.gymadmin.platform.domain.model.Caracteristica;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CaracteristicaUseCase {

    Flux<Caracteristica> listarCaracteristicas();

    Mono<Caracteristica> crearCaracteristica(CrearCaracteristicaCommand command);

    record CrearCaracteristicaCommand(
            String codigo,
            String nombre,
            String modulo
    ) {}
}
