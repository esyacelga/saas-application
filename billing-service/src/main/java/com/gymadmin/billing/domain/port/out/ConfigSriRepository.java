package com.gymadmin.billing.domain.port.out;

import com.gymadmin.billing.domain.model.ConfigSri;
import reactor.core.publisher.Mono;

public interface ConfigSriRepository {

    Mono<ConfigSri> findByEmpresa(Integer idCompania, Integer idSucursal);

    Mono<ConfigSri> findFirstByCompania(Integer idCompania);
}
