package com.gymadmin.billing.domain.port.out;

import com.gymadmin.billing.domain.model.ColaEnvio;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ColaEnvioRepository {
    Mono<ColaEnvio> save(ColaEnvio cola);
    Mono<ColaEnvio> update(ColaEnvio cola);
    Flux<ColaEnvio> findPendientes(int limit);
    Mono<ColaEnvio> findLatestByIdComprobante(Long idComprobante);
}
