package com.gymadmin.auth.domain.port.out;

import com.gymadmin.auth.domain.model.UsuarioApp;
import reactor.core.publisher.Mono;

public interface UsuarioAppPort {
    Mono<UsuarioApp> findByLoginAndIdCompania(String login, Integer idCompania);
    Mono<UsuarioApp> findById(Integer id);
    Mono<Boolean> existsByIdPersonaAndIdCompania(Integer idPersona, Integer idCompania);
    Mono<UsuarioApp> findByPersonaCiAndIdCompania(String ci, Integer idCompania);
    Mono<UsuarioApp> findByTokenRecuperacion(String token);
    Mono<UsuarioApp> save(UsuarioApp usuario);
    reactor.core.publisher.Flux<UsuarioApp> findByIdPersona(Integer idPersona);
}
