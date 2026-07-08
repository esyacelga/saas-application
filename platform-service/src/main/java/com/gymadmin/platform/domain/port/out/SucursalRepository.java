package com.gymadmin.platform.domain.port.out;

import com.gymadmin.platform.domain.model.Sucursal;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface SucursalRepository {

    Flux<Sucursal> findByIdCompania(Long idCompania);

    Mono<Sucursal> findById(Long id);

    Mono<Sucursal> findByQrToken(String qrToken);

    Mono<Sucursal> save(Sucursal sucursal);

    Mono<Sucursal> update(Sucursal sucursal);
}
