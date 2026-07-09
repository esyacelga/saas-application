package com.gymadmin.platform.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.platform.domain.model.Compania;
import com.gymadmin.platform.domain.port.out.CompaniaRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.CompaniaEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.CompaniaR2dbcRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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
                    // trial_usado NO se toca aquí: es irrevocable (RN-01) y se actualiza en el flujo
                    // "activar Trial" de la Sub-fase 1.3.
                    return repository.save(existing);
                })
                .map(this::toDomain);
    }

    private Compania toDomain(CompaniaEntity entity) {
        Compania c = new Compania(
                entity.getId(),
                entity.getNombre(),
                entity.getRuc(),
                entity.getLogoUrl(),
                entity.getTelefono(),
                entity.getWhatsapp(),
                entity.getCorreo(),
                entity.getActivo()
        );
        c.setTrialUsado(Boolean.TRUE.equals(entity.getTrialUsado()));
        if (entity.getFechaTrialUsado() != null) {
            c.setFechaTrialUsado(entity.getFechaTrialUsado().toInstant());
        }
        return c;
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
        entity.setTrialUsado(c.isTrialUsado());
        if (c.getFechaTrialUsado() != null) {
            entity.setFechaTrialUsado(OffsetDateTime.ofInstant(c.getFechaTrialUsado(), ZoneOffset.UTC));
        }
        return entity;
    }
}
