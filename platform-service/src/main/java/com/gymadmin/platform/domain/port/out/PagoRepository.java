package com.gymadmin.platform.domain.port.out;

import com.gymadmin.platform.domain.model.PagoSuscripcion;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PagoRepository {

    Flux<PagoSuscripcion> findByIdCompania(Long idCompania);

    Mono<PagoSuscripcion> findById(Long id);

    Mono<PagoSuscripcion> save(PagoSuscripcion pago);

    Mono<PagoSuscripcion> update(PagoSuscripcion pago);
}
