package com.gymadmin.billing.domain.port.out;

import com.gymadmin.billing.domain.model.EnvioSri;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface EnvioSriRepository {
    Mono<EnvioSri> save(EnvioSri envio);
    Flux<EnvioSri> findByIdComprobante(Long idComprobante);
}
