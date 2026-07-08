package com.gymadmin.platform.domain.port.out;

import com.gymadmin.platform.domain.model.NotificacionSuscripcion;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface NotificacionRepository {

    Mono<NotificacionSuscripcion> save(NotificacionSuscripcion notificacion);

    Flux<NotificacionSuscripcion> findByIdCompaniaPlan(Long idCompaniaPlan);

    Mono<Boolean> existsByIdCompaniaPlanAndDiasAntes(Long idCompaniaPlan, Integer diasAntes);
}
