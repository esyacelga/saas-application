package com.gymadmin.platform.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.NotifBucketGlobalEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

/**
 * Fase 6 (R1) — story GYM-003, {@code saas.notif_buckets_globales}.
 * PK es {@code destinatario VARCHAR(10)} ('socio'/'dueno'), por eso el tipo del ID es String.
 */
public interface NotifBucketGlobalR2dbcRepository
        extends ReactiveCrudRepository<NotifBucketGlobalEntity, String> {
}
