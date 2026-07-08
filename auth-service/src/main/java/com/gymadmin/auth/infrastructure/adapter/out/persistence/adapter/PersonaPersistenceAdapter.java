package com.gymadmin.auth.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.auth.domain.model.Persona;
import com.gymadmin.auth.domain.port.out.PersonaPort;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.mapper.PersonaMapper;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.repository.PersonaR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class PersonaPersistenceAdapter implements PersonaPort {

    private final PersonaR2dbcRepository repository;

    @Override
    public Mono<Persona> findByCi(String ci) {
        return repository.findByCi(ci).map(PersonaMapper::toDomain);
    }

    @Override
    public Mono<Boolean> existsByCi(String ci) {
        return repository.existsByCi(ci);
    }

    @Override
    public Mono<Boolean> existsByCiAndIdNot(String ci, Integer id) {
        return repository.existsByCiAndIdNot(ci, id);
    }

    @Override
    public Mono<Persona> findById(Integer id) {
        return repository.findById(id).map(PersonaMapper::toDomain);
    }

    @Override
    public Mono<Persona> save(Persona persona) {
        return repository.save(PersonaMapper.toEntity(persona)).map(PersonaMapper::toDomain);
    }

    @Override
    public Mono<Persona> findByCorreo(String correo) {
        return repository.findByCorreo(correo).map(PersonaMapper::toDomain);
    }

    @Override
    public Flux<Persona> findAll(String nombre, String ci, String correo, String sexo, int offset, int limit) {
        return repository.findAllFiltered(nombre, ci, correo, sexo, limit, offset)
                .map(PersonaMapper::toDomain);
    }

    @Override
    public Mono<Long> countAll(String nombre, String ci, String correo, String sexo) {
        return repository.countAllFiltered(nombre, ci, correo, sexo);
    }
}
