package com.gymadmin.auth.domain.port.out;

import reactor.core.publisher.Mono;

public interface FacebookTokenVerifierPort {
    Mono<String> verifyAndGetEmail(String accessToken);
}
