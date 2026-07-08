package com.gymadmin.platform.domain.port.out;

import com.gymadmin.platform.domain.model.ConfigNotifSuscripcion;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ConfigNotifRepository {

    Flux<ConfigNotifSuscripcion> findByIdCompania(Long idCompania);

    Mono<Void> replaceAll(Long idCompania, List<ConfigNotifSuscripcion> configs);

    Mono<Void> saveAll(List<ConfigNotifSuscripcion> configs);
}
