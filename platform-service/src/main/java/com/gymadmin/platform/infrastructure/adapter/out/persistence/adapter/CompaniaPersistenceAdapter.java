package com.gymadmin.platform.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.platform.domain.model.Compania;
import com.gymadmin.platform.domain.port.out.CompaniaRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.CompaniaEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.CompaniaR2dbcRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class CompaniaPersistenceAdapter implements CompaniaRepository {

    private final CompaniaR2dbcRepository repository;

    public CompaniaPersistenceAdapter(CompaniaR2dbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public Flux<Compania> findAll() {
        return repository.findAll().map(this::toDomain);
    }

    @Override
    public Mono<Compania> findById(Long id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Mono<Compania> findByRuc(String ruc) {
        return repository.findByRuc(ruc).map(this::toDomain);
    }

    @Override
    public Mono<Compania> save(Compania compania) {
        CompaniaEntity entity = toEntity(compania);
        entity.setActivo(true);
        return repository.save(entity).map(this::toDomain);
    }

    @Override
    public Mono<Compania> update(Compania compania) {
        return repository.findById(compania.getId())
                .flatMap(existing -> {
                    existing.setNombre(compania.getNombre());
                    existing.setLogoUrl(compania.getLogoUrl());
                    existing.setTelefono(compania.getTelefono());
                    existing.setWhatsapp(compania.getWhatsapp());
                    existing.setCorreo(compania.getCorreo());
                    existing.setActivo(compania.getActivo());
                    return repository.save(existing);
                })
                .map(this::toDomain);
    }

    private Compania toDomain(CompaniaEntity entity) {
        return new Compania(
                entity.getId(),
                entity.getNombre(),
                entity.getRuc(),
                entity.getLogoUrl(),
                entity.getTelefono(),
                entity.getWhatsapp(),
                entity.getCorreo(),
                entity.getActivo()
        );
    }

    private CompaniaEntity toEntity(Compania c) {
        CompaniaEntity entity = new CompaniaEntity();
        entity.setId(c.getId());
        entity.setNombre(c.getNombre());
        entity.setRuc(c.getRuc());
        entity.setLogoUrl(c.getLogoUrl());
        entity.setTelefono(c.getTelefono());
        entity.setWhatsapp(c.getWhatsapp());
        entity.setCorreo(c.getCorreo());
        entity.setActivo(c.getActivo());
        return entity;
    }
}
