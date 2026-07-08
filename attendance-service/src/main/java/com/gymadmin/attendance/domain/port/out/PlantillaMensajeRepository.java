package com.gymadmin.attendance.domain.port.out;

import com.gymadmin.attendance.domain.model.PlantillaMensaje;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PlantillaMensajeRepository {

    Flux<PlantillaMensaje> findByCompania(Integer idCompania);

    Mono<PlantillaMensaje> findById(Integer id);

    Mono<PlantillaMensaje> save(PlantillaMensaje plantilla);

    Mono<PlantillaMensaje> update(PlantillaMensaje plantilla);

    Mono<Long> countActivasByTipo(Integer idCompania, String tipo);

    Mono<PlantillaMensaje> findRandomActivaByTipo(Integer idCompania, String tipo);

    Mono<Void> softDelete(Integer id);
}
