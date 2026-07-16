package com.gymadmin.platform.domain.port.out;

import com.gymadmin.platform.domain.model.NotifBucketGlobal;
import com.gymadmin.platform.domain.model.NotifBucketGlobal.Destinatario;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Fase 6 (R1): puerto out para la política global de buckets de aviso previo
 * ({@code saas.notif_buckets_globales}). Config de plataforma (no por tenant).
 */
public interface NotifBucketGlobalRepository {

    Flux<NotifBucketGlobal> findAll();

    Mono<NotifBucketGlobal> findByDestinatario(Destinatario destinatario);

    Mono<NotifBucketGlobal> save(NotifBucketGlobal bucket);
}
