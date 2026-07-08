package com.gymadmin.auth.domain.port.out;

import reactor.core.publisher.Mono;

public interface RateLimiterPort {
    Mono<Void> checkAndRecord(String key);
    Mono<Void> reset(String key);
    Mono<Void> clearAll();
}
