package com.gymadmin.auth.domain.port.out;

import com.gymadmin.auth.domain.model.OAuthProfile;
import reactor.core.publisher.Mono;

public interface FacebookTokenVerifierPort {
    Mono<String> verifyAndGetEmail(String accessToken);

    /**
     * Verifica el access_token contra Facebook Graph API y devuelve email + nombre.
     * Falla con {@code AuthException} si el token es invalido o el usuario no autorizo el permiso de email.
     */
    Mono<OAuthProfile> verifyAndGetProfile(String accessToken);
}
