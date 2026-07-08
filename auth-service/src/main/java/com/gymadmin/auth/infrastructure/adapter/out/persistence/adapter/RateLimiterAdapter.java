package com.gymadmin.auth.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.auth.domain.exception.TooManyRequestsException;
import com.gymadmin.auth.domain.port.out.RateLimiterPort;
import com.gymadmin.auth.infrastructure.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class RateLimiterAdapter implements RateLimiterPort {

    private final AppProperties props;

    private record Attempt(int count, Instant lockedUntil) {}
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> checkAndRecord(String key) {
        return Mono.fromRunnable(() -> {
            Attempt current = attempts.getOrDefault(key, new Attempt(0, null));
            if (current.lockedUntil() != null && Instant.now().isBefore(current.lockedUntil()))
                throw new TooManyRequestsException(
                        "Demasiados intentos fallidos. Intente en " + props.getLoginLockoutMinutes() + " minutos.");
            int newCount = current.count() + 1;
            if (newCount >= props.getMaxLoginAttempts()) {
                Instant lockUntil = Instant.now().plusSeconds(props.getLoginLockoutMinutes() * 60L);
                attempts.put(key, new Attempt(newCount, lockUntil));
                throw new TooManyRequestsException(
                        "Demasiados intentos fallidos. Intente en " + props.getLoginLockoutMinutes() + " minutos.");
            }
            attempts.put(key, new Attempt(newCount, null));
        });
    }

    @Override
    public Mono<Void> reset(String key) {
        return Mono.fromRunnable(() -> attempts.remove(key));
    }

    @Override
    public Mono<Void> clearAll() {
        return Mono.fromRunnable(attempts::clear);
    }
}
