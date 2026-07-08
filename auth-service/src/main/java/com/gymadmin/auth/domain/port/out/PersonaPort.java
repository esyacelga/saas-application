package com.gymadmin.auth.domain.port.out;

import com.gymadmin.auth.domain.model.Persona;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PersonaPort {
    Mono<Persona> findByCi(String ci);
    Mono<Boolean> existsByCi(String ci);
    Mono<Boolean> existsByCiAndIdNot(String ci, Integer id);
    Mono<Persona> findById(Integer id);
    Mono<Persona> save(Persona persona);
    Mono<Persona> findByCorreo(String correo);
    Flux<Persona> findAll(String nombre, String ci, String correo, String sexo, int offset, int limit);
    Mono<Long> countAll(String nombre, String ci, String correo, String sexo);
}
