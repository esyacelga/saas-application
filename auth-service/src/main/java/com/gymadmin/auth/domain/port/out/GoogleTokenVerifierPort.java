package com.gymadmin.auth.domain.port.out;

import reactor.core.publisher.Mono;

public interface GoogleTokenVerifierPort {
    Mono<String> verifyAndGetEmail(String idToken);
}
