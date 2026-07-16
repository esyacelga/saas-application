package com.gymadmin.platform.domain.port.out;

import com.gymadmin.platform.domain.model.Compania;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Collection;

public interface CompaniaRepository {

    Flux<Compania> findAll();

    Mono<Compania> findById(Long id);

    Mono<Compania> findByRuc(String ruc);

    Mono<Compania> save(Compania compania);

    Mono<Compania> update(Compania compania);

    /**
     * Fase 6 (bloque E): actualiza SOLO el opt-in de WhatsApp del dueño ({@code acepta_whatsapp} +
     * {@code fecha_consentimiento_wa}), sin tocar el resto de campos. El {@code update()} general
     * deliberadamente no toca el opt-in, así que este método es el único punto de escritura del
     * consentimiento. {@code fecha} debe ser el momento del opt-in cuando {@code acepta=true}, y
     * {@code null} cuando {@code acepta=false} (opt-out).
     */
    Mono<Compania> updateConsentimientoWa(Long id, boolean acepta, Instant fecha);

    /**
     * REQ-SAAS-001 (Sub-fase 1.6, item #4): batch fetch por lista de IDs para
     * enriquecer respuestas (p.ej. mapear id_compania -&gt; nombre en la bandeja
     * de pagos pendientes) con un único query. Si {@code ids} es null o vacío,
     * debe emitir {@code Flux.empty()}.
     */
    Flux<Compania> findAllByIds(Collection<Long> ids);
}
