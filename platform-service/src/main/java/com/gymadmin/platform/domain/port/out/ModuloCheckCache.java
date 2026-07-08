package com.gymadmin.platform.domain.port.out;

import com.gymadmin.platform.domain.model.ModuloCheckResult;
import reactor.core.publisher.Mono;

import java.time.Duration;

public interface ModuloCheckCache {

    Mono<ModuloCheckResult> get(Long idCompania, String codigo);

    Mono<Void> put(Long idCompania, String codigo, ModuloCheckResult result, Duration ttl);

    Mono<Void> evict(Long idCompania);
}
