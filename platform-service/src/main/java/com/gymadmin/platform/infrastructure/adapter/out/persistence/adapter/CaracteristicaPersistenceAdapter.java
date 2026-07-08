package com.gymadmin.platform.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.platform.domain.model.Caracteristica;
import com.gymadmin.platform.domain.port.out.CaracteristicaRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.CaracteristicaEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.CaracteristicaR2dbcRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class CaracteristicaPersistenceAdapter implements CaracteristicaRepository {

    private final CaracteristicaR2dbcRepository repository;

    public CaracteristicaPersistenceAdapter(CaracteristicaR2dbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public Flux<Caracteristica> findAll() {
        return repository.findAll().map(this::toDomain);
    }

    @Override
    public Mono<Caracteristica> findById(Long id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Mono<Caracteristica> save(Caracteristica caracteristica) {
        CaracteristicaEntity entity = toEntity(caracteristica);
        entity.setActivo(true);
        return repository.save(entity).map(this::toDomain);
    }

    @Override
    public Mono<Caracteristica> findByCodigo(String codigo) {
        return repository.findByCodigo(codigo).map(this::toDomain);
    }

    private Caracteristica toDomain(CaracteristicaEntity entity) {
        return new Caracteristica(
                entity.getId(),
                entity.getCodigo(),
                entity.getNombre(),
                entity.getModulo(),
                entity.getActivo()
        );
    }

    private CaracteristicaEntity toEntity(Caracteristica c) {
        CaracteristicaEntity entity = new CaracteristicaEntity();
        entity.setId(c.getId());
        entity.setCodigo(c.getCodigo());
        entity.setNombre(c.getNombre());
        entity.setModulo(c.getModulo());
        entity.setActivo(c.getActivo());
        return entity;
    }
}
