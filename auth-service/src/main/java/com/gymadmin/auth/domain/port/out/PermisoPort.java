package com.gymadmin.auth.domain.port.out;

import com.gymadmin.auth.domain.model.Permiso;
import reactor.core.publisher.Flux;

import java.util.Collection;

public interface PermisoPort {
    Flux<Permiso> findByIdCompania(Integer idCompania);
    Flux<Permiso> findByIdInAndIdCompania(Collection<Integer> ids, Integer idCompania);
}
