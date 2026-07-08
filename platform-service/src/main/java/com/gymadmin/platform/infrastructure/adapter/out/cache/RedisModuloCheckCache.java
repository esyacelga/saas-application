package com.gymadmin.platform.infrastructure.adapter.out.cache;

import com.gymadmin.platform.domain.model.ModuloCheckResult;
import com.gymadmin.platform.domain.port.out.ModuloCheckCache;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

// Redis eliminado temporalmente para despliegue en Cloud Run sin dependencias externas.
// Ver docs/REDIS_REMOVAL.md para instrucciones de reintegración.
@Component
public class RedisModuloCheckCache implements ModuloCheckCache {

    @Override
    public Mono<ModuloCheckResult> get(Long idCompania, String codigo) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> put(Long idCompania, String codigo, ModuloCheckResult result, Duration ttl) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> evict(Long idCompania) {
        return Mono.empty();
    }
}
