package com.gymadmin.platform.domain.port.in;

import com.gymadmin.platform.domain.model.NotifBucketGlobal;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Fase 6 (R1): casos de uso de la política global de buckets de aviso previo
 * ({@code saas.notif_buckets_globales}). Solo super_admin (gate en el controller).
 */
public interface NotifBucketsUseCase {

    /** Lista los buckets de todos los destinatarios (socio + dueno). */
    Flux<NotifBucketGlobal> listar();

    /**
     * Actualiza el bucket de un destinatario. {@code destinatario} es el código ('socio'/'dueno').
     * {@code modificadoPor} es el id del usuario de plataforma que edita (puede ser {@code null}).
     */
    Mono<NotifBucketGlobal> actualizar(String destinatario, int diasPrevio, boolean activo, Long modificadoPor);
}
