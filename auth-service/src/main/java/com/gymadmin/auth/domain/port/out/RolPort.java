package com.gymadmin.auth.domain.port.out;

import com.gymadmin.auth.domain.model.Rol;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface RolPort {
    Flux<Rol> findByIdCompania(Integer idCompania);
    Mono<Rol> findByIdAndIdCompania(Integer id, Integer idCompania);
    Mono<Boolean> existsByIdCompaniaAndNombre(Integer idCompania, String nombre);
    Mono<Rol> save(Rol rol);
    Mono<Void> deleteById(Integer id);
}
