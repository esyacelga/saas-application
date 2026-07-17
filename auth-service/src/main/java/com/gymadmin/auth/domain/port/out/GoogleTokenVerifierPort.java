package com.gymadmin.auth.domain.port.out;

import com.gymadmin.auth.domain.model.OAuthProfile;
import reactor.core.publisher.Mono;

public interface GoogleTokenVerifierPort {
    Mono<String> verifyAndGetEmail(String idToken);

    /**
     * Verifica el id_token contra Google y devuelve email + nombre (cuando esta disponible).
     * Falla con {@code AuthException} si el token es invalido o el email no esta verificado.
     */
    Mono<OAuthProfile> verifyAndGetProfile(String idToken);
}
