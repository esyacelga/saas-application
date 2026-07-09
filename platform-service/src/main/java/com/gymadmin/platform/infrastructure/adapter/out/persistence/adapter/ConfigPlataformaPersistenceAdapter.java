package com.gymadmin.platform.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.platform.domain.model.ConfigPlataforma;
import com.gymadmin.platform.domain.port.out.ConfigPlataformaRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.ConfigPlataformaEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.ConfigPlataformaR2dbcRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Persistence adapter para {@code saas.config_plataforma} (sección 11.4).
 * <p>
 * La PK es la {@code clave} (String). El {@link ConfigPlataformaEntity} implementa
 * {@link org.springframework.data.domain.Persistable} para que R2DBC diferencie
 * INSERT de UPDATE — el flag {@code nuevo} se ajusta detectando si la clave ya
 * existe en DB.
 */
@Component
public class ConfigPlataformaPersistenceAdapter implements ConfigPlataformaRepository {

    private final ConfigPlataformaR2dbcRepository repository;

    public ConfigPlataformaPersistenceAdapter(ConfigPlataformaR2dbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<ConfigPlataforma> findByClave(String clave) {
        return repository.findById(clave).map(this::toDomain);
    }

    @Override
    public Mono<ConfigPlataforma> save(ConfigPlataforma config) {
        return repository.findById(config.getClave())
                .map(existing -> {
                    ConfigPlataformaEntity entity = toEntity(config);
                    entity.setCreacionFecha(existing.getCreacionFecha());
                    entity.setCreacionUsuario(existing.getCreacionUsuario());
                    entity.setNuevo(false);
                    return entity;
                })
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    ConfigPlataformaEntity entity = toEntity(config);
                    entity.setNuevo(true);
                    return entity;
                }))
                .flatMap(repository::save)
                .map(this::toDomain);
    }

    private ConfigPlataforma toDomain(ConfigPlataformaEntity entity) {
        ConfigPlataforma c = new ConfigPlataforma();
        c.setClave(entity.getClave());
        c.setValor(entity.getValor());
        c.setDescripcion(entity.getDescripcion());
        c.setModificadoPor(entity.getModificadoPor());
        if (entity.getModificadoAt() != null) {
            c.setModificadoAt(entity.getModificadoAt().toInstant());
        }
        return c;
    }

    private ConfigPlataformaEntity toEntity(ConfigPlataforma c) {
        ConfigPlataformaEntity entity = new ConfigPlataformaEntity();
        entity.setClave(c.getClave());
        entity.setValor(c.getValor());
        entity.setDescripcion(c.getDescripcion());
        entity.setModificadoPor(c.getModificadoPor());
        if (c.getModificadoAt() != null) {
            entity.setModificadoAt(OffsetDateTime.ofInstant(c.getModificadoAt(), ZoneOffset.UTC));
        } else {
            entity.setModificadoAt(OffsetDateTime.now());
        }
        return entity;
    }
}
