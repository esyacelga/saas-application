package com.gymadmin.platform.domain.port.in;

import com.gymadmin.platform.domain.model.ConfigNotifSuscripcion;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface NotifConfigUseCase {

    Flux<ConfigNotifSuscripcion> getConfig(Long idCompania);

    Mono<Void> updateConfig(Long idCompania, List<ConfigEntry> configs);

    record ConfigEntry(
            Integer diasAntes,
            String canal,
            Boolean activo
    ) {}
}
