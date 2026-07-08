package com.gymadmin.auth.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.auth.domain.model.Rol;
import com.gymadmin.auth.domain.port.out.RolPort;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.mapper.RolMapper;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.repository.RolR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class RolPersistenceAdapter implements RolPort {

    private final RolR2dbcRepository repository;

    @Override
    public Flux<Rol> findByIdCompania(Integer idCompania) {
        return repository.findByIdCompania(idCompania).map(RolMapper::toDomain);
    }

    @Override
    public Mono<Rol> findByIdAndIdCompania(Integer id, Integer idCompania) {
        return repository.findByIdAndIdCompania(id, idCompania).map(RolMapper::toDomain);
    }

    @Override
    public Mono<Boolean> existsByIdCompaniaAndNombre(Integer idCompania, String nombre) {
        return repository.existsByIdCompaniaAndNombre(idCompania, nombre);
    }

    @Override
    public Mono<Rol> save(Rol rol) {
        return repository.save(RolMapper.toEntity(rol)).map(RolMapper::toDomain);
    }

    @Override
    public Mono<Void> deleteById(Integer id) {
        return repository.deleteById(id);
    }
}
