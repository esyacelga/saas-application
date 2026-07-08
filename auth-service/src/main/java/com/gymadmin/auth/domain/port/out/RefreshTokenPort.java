package com.gymadmin.auth.domain.port.out;

import com.gymadmin.auth.domain.model.RefreshToken;
import reactor.core.publisher.Mono;

public interface RefreshTokenPort {
    Mono<RefreshToken> findByToken(String token);
    Mono<RefreshToken> save(RefreshToken token);
    Mono<Void> delete(RefreshToken token);
    Mono<Void> deleteByIdUsuarioAndTipoUsuario(Integer idUsuario, String tipoUsuario);
}
