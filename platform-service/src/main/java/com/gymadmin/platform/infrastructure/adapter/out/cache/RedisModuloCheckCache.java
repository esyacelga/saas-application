package com.gymadmin.platform.infrastructure.adapter.out.cache;

import com.gymadmin.platform.domain.model.ModuloCheckResult;
import com.gymadmin.platform.domain.port.out.ModuloCheckCache;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

// Redis eliminado temporalmente para despliegue en Cloud Run sin dependencias externas.
// Ver docs/REDIS_REMOVAL.md para instrucciones de reintegración.
// Cuando Redis se reactive:
//   - inyectar ReactiveRedisTemplate<String,String>
//   - get/put usan opsForValue() con TTL
//   - invalidateByCompania hace: keys("modulo_check:{idCompania}:*")
//     seguido de delete(...) — o SCAN reactivo para no bloquear en producción
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

    /**
     * REQ-SAAS-001 — Con Redis presente ejecuta SCAN con patrón
     * {@code modulo_check:{idCompania}:*} seguido de DEL sobre los keys hallados.
     * Actualmente Redis está desactivado (ver docs/REDIS_REMOVAL.md) por lo que
     * retorna 0 como no-op seguro.
     */
    @Override
    public Mono<Long> invalidateByCompania(Long idCompania) {
        return Mono.just(0L);
    }
}
