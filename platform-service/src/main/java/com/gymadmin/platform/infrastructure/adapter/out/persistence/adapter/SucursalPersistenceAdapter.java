package com.gymadmin.platform.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.platform.domain.model.Sucursal;
import com.gymadmin.platform.domain.port.out.SucursalRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.SucursalEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.SucursalR2dbcRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class SucursalPersistenceAdapter implements SucursalRepository {

    private final SucursalR2dbcRepository repository;

    public SucursalPersistenceAdapter(SucursalR2dbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public Flux<Sucursal> findByIdCompania(Long idCompania) {
        return repository.findByIdCompania(idCompania).map(this::toDomain);
    }

    @Override
    public Mono<Sucursal> findById(Long id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Mono<Sucursal> findByQrToken(String qrToken) {
        return repository.findByQrToken(qrToken).map(this::toDomain);
    }

    @Override
    public Mono<Sucursal> save(Sucursal sucursal) {
        SucursalEntity entity = toEntity(sucursal);
        entity.setActivo(true);
        return repository.save(entity).map(this::toDomain);
    }

    @Override
    public Mono<Sucursal> update(Sucursal sucursal) {
        return repository.findById(sucursal.getId())
                .flatMap(existing -> {
                    existing.setNombre(sucursal.getNombre());
                    existing.setDireccion(sucursal.getDireccion());
                    existing.setQrToken(sucursal.getQrToken());
                    existing.setQrTokenExpira(sucursal.getQrTokenExpira());
                    existing.setActivo(sucursal.getActivo());
                    return repository.save(existing);
                })
                .map(this::toDomain);
    }

    private Sucursal toDomain(SucursalEntity entity) {
        return new Sucursal(
                entity.getId(),
                entity.getIdCompania(),
                entity.getNombre(),
                entity.getDireccion(),
                entity.getEsPrincipal(),
                entity.getActivo(),
                entity.getQrToken(),
                entity.getQrTokenExpira()
        );
    }

    private SucursalEntity toEntity(Sucursal s) {
        SucursalEntity entity = new SucursalEntity();
        entity.setId(s.getId());
        entity.setIdCompania(s.getIdCompania());
        entity.setNombre(s.getNombre());
        entity.setDireccion(s.getDireccion());
        entity.setEsPrincipal(s.getEsPrincipal());
        entity.setActivo(s.getActivo());
        entity.setQrToken(s.getQrToken());
        entity.setQrTokenExpira(s.getQrTokenExpira());
        return entity;
    }
}
