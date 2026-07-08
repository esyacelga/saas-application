package com.gymadmin.auth.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.auth.domain.model.Permiso;
import com.gymadmin.auth.domain.port.out.PermisoPort;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.mapper.PermisoMapper;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.repository.PermisoR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Collection;

@Component
@RequiredArgsConstructor
public class PermisoPersistenceAdapter implements PermisoPort {

    private final PermisoR2dbcRepository repository;

    @Override
    public Flux<Permiso> findByIdCompania(Integer idCompania) {
        return repository.findByIdCompania(idCompania).map(PermisoMapper::toDomain);
    }

    @Override
    public Flux<Permiso> findByIdInAndIdCompania(Collection<Integer> ids, Integer idCompania) {
        return repository.findByIdInAndIdCompania(ids, idCompania).map(PermisoMapper::toDomain);
    }
}
