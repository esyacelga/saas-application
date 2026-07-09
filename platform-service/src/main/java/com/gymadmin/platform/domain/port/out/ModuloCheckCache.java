package com.gymadmin.platform.domain.port.out;

import com.gymadmin.platform.domain.model.ModuloCheckResult;
import reactor.core.publisher.Mono;

import java.time.Duration;

public interface ModuloCheckCache {

    Mono<ModuloCheckResult> get(Long idCompania, String codigo);

    Mono<Void> put(Long idCompania, String codigo, ModuloCheckResult result, Duration ttl);

    Mono<Void> evict(Long idCompania);

    /**
     * REQ-SAAS-001 — invalida en Redis todas las entradas cacheadas para el
     * tenant indicado usando el patrón {@code modulo_check:{idCompania}:*}
     * (SCAN + DEL). Retorna la cantidad de keys borradas.
     * <p>
     * Uso: tras una transición de suscripción o cambio de plan que altere el
     * conjunto de módulos disponibles del tenant.
     */
    Mono<Long> invalidateByCompania(Long idCompania);
}
