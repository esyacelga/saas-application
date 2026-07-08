package com.gymadmin.auth.domain.port.out;

import com.gymadmin.auth.domain.model.UsuarioPlataforma;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UsuarioPlataformaPort {
    Mono<UsuarioPlataforma> findByCorreo(String correo);
    Mono<UsuarioPlataforma> findById(Integer id);
    Mono<Boolean> existsByCorreo(String correo);
    Flux<UsuarioPlataforma> findAll();
    Flux<UsuarioPlataforma> findByIdPersona(Integer idPersona);
    Mono<UsuarioPlataforma> save(UsuarioPlataforma usuario);
    Mono<Long> countByRolAndActivoTrue(String rol);
}
