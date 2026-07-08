package com.gymadmin.auth.domain.port.out;

import reactor.core.publisher.Mono;

public interface EmailPort {
    Mono<Void> sendPasswordResetEmail(String to, String nombre, String resetLink);
}
