package com.gymadmin.platform.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.platform.domain.model.NotifBucketGlobal;
import com.gymadmin.platform.domain.model.NotifBucketGlobal.Destinatario;
import com.gymadmin.platform.domain.port.out.NotifBucketGlobalRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.NotifBucketGlobalEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.NotifBucketGlobalR2dbcRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Persistence adapter para {@code saas.notif_buckets_globales} (Fase 6 / R1).
 * <p>
 * La PK es {@code destinatario} (String). El {@link NotifBucketGlobalEntity} implementa
 * {@link org.springframework.data.domain.Persistable}; el flag {@code nuevo} se ajusta detectando si la
 * fila ya existe. En la práctica las 2 filas ('socio'/'dueno') se seedan en la migración, así que
 * {@link #save} siempre entra por la rama UPDATE (preserva {@code creacion_*}).
 */
@Component
public class NotifBucketGlobalPersistenceAdapter implements NotifBucketGlobalRepository {

    private final NotifBucketGlobalR2dbcRepository repository;

    public NotifBucketGlobalPersistenceAdapter(NotifBucketGlobalR2dbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public Flux<NotifBucketGlobal> findAll() {
        return repository.findAll().map(this::toDomain);
    }

    @Override
    public Mono<NotifBucketGlobal> findByDestinatario(Destinatario destinatario) {
        return repository.findById(destinatario.getCodigo()).map(this::toDomain);
    }

    @Override
    public Mono<NotifBucketGlobal> save(NotifBucketGlobal bucket) {
        String pk = bucket.getDestinatario().getCodigo();
        return repository.findById(pk)
                .map(existing -> {
                    NotifBucketGlobalEntity entity = toEntity(bucket);
                    entity.setCreacionFecha(existing.getCreacionFecha());
                    entity.setCreacionUsuario(existing.getCreacionUsuario());
                    entity.setNuevo(false);
                    return entity;
                })
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    NotifBucketGlobalEntity entity = toEntity(bucket);
                    entity.setCreacionUsuario("sistema");
                    entity.setNuevo(true);
                    return entity;
                }))
                .flatMap(repository::save)
                .map(this::toDomain);
    }

    private NotifBucketGlobal toDomain(NotifBucketGlobalEntity entity) {
        NotifBucketGlobal b = new NotifBucketGlobal();
        b.setDestinatario(Destinatario.fromCodigo(entity.getDestinatario()));
        b.setDiasPrevio(entity.getDiasPrevio() != null ? entity.getDiasPrevio() : 0);
        b.setActivo(Boolean.TRUE.equals(entity.getActivo()));
        b.setModificadoPor(entity.getModificadoPor());
        if (entity.getModificadoAt() != null) {
            b.setModificadoAt(entity.getModificadoAt().toInstant());
        }
        return b;
    }

    private NotifBucketGlobalEntity toEntity(NotifBucketGlobal b) {
        NotifBucketGlobalEntity entity = new NotifBucketGlobalEntity();
        entity.setDestinatario(b.getDestinatario().getCodigo());
        entity.setDiasPrevio(b.getDiasPrevio());
        entity.setActivo(b.isActivo());
        entity.setModificadoPor(b.getModificadoPor());
        entity.setModificadoAt(b.getModificadoAt() != null
                ? OffsetDateTime.ofInstant(b.getModificadoAt(), ZoneOffset.UTC)
                : OffsetDateTime.now());
        return entity;
    }
}
