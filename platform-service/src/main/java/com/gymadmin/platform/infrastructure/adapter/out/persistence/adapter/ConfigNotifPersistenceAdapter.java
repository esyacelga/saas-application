package com.gymadmin.platform.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.platform.domain.model.ConfigNotifSuscripcion;
import com.gymadmin.platform.domain.port.out.ConfigNotifRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.ConfigNotifR2dbcRepository;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
public class ConfigNotifPersistenceAdapter implements ConfigNotifRepository {

    private final ConfigNotifR2dbcRepository repository;
    private final DatabaseClient databaseClient;

    public ConfigNotifPersistenceAdapter(ConfigNotifR2dbcRepository repository,
                                         DatabaseClient databaseClient) {
        this.repository = repository;
        this.databaseClient = databaseClient;
    }

    @Override
    public Flux<ConfigNotifSuscripcion> findByIdCompania(Long idCompania) {
        return repository.findByIdCompania(idCompania).map(this::toDomain);
    }

    @Override
    public Mono<Void> replaceAll(Long idCompania, List<ConfigNotifSuscripcion> configs) {
        return repository.deleteByIdCompania(idCompania)
                .thenMany(Flux.fromIterable(configs)
                        .flatMap(this::insertConfig))
                .then();
    }

    @Override
    public Mono<Void> saveAll(List<ConfigNotifSuscripcion> configs) {
        return Flux.fromIterable(configs)
                .flatMap(this::insertConfig)
                .then();
    }

    private Mono<Void> insertConfig(ConfigNotifSuscripcion c) {
        String canal = c.getCanal() != null ? c.getCanal().name().toLowerCase() : null;
        return databaseClient.sql(
                "INSERT INTO tenant.config_notif_suscripcion (id_compania, dias_antes, canal, activo) " +
                "VALUES (:idCompania, :diasAntes, :canal, :activo) " +
                "ON CONFLICT (id_compania, dias_antes) DO UPDATE " +
                "SET canal = EXCLUDED.canal, activo = EXCLUDED.activo"
        )
                .bind("idCompania", c.getIdCompania())
                .bind("diasAntes", c.getDiasAntes())
                .bind("canal", canal)
                .bind("activo", c.getActivo())
                .then();
    }

    private ConfigNotifSuscripcion toDomain(Object entity) {
        // This method is used with the R2DBC repository for reads
        var e = (com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.ConfigNotifSuscripcionEntity) entity;
        ConfigNotifSuscripcion c = new ConfigNotifSuscripcion();
        c.setIdCompania(e.getIdCompania());
        c.setDiasAntes(e.getDiasAntes());
        if (e.getCanal() != null) {
            c.setCanal(ConfigNotifSuscripcion.Canal.valueOf(e.getCanal().toUpperCase()));
        }
        c.setActivo(e.getActivo());
        return c;
    }
}
