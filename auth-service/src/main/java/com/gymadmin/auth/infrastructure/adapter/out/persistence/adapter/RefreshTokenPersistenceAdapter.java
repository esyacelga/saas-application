package com.gymadmin.auth.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.auth.domain.model.RefreshToken;
import com.gymadmin.auth.domain.port.out.RefreshTokenPort;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.mapper.RefreshTokenMapper;
import com.gymadmin.auth.infrastructure.adapter.out.persistence.repository.RefreshTokenR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class RefreshTokenPersistenceAdapter implements RefreshTokenPort {

    private final RefreshTokenR2dbcRepository repository;

    @Override
    public Mono<RefreshToken> findByToken(String token) {
        return repository.findByToken(token).map(RefreshTokenMapper::toDomain);
    }

    @Override
    public Mono<RefreshToken> save(RefreshToken token) {
        return repository.save(RefreshTokenMapper.toEntity(token)).map(RefreshTokenMapper::toDomain);
    }

    @Override
    public Mono<Void> delete(RefreshToken token) {
        return repository.delete(RefreshTokenMapper.toEntity(token));
    }

    @Override
    public Mono<Void> deleteByIdUsuarioAndTipoUsuario(Integer idUsuario, String tipoUsuario) {
        return repository.deleteByIdUsuarioAndTipoUsuario(idUsuario, tipoUsuario);
    }
}
