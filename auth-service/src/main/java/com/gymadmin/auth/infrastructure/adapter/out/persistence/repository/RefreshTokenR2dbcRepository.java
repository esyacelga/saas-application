package com.gymadmin.auth.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.RefreshTokenEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface RefreshTokenR2dbcRepository extends ReactiveCrudRepository<RefreshTokenEntity, Long> {
    Mono<RefreshTokenEntity> findByToken(String token);

    @Modifying
    @Query("DELETE FROM seguridad.refresh_tokens WHERE id_usuario = :idUsuario AND tipo_usuario = :tipoUsuario")
    Mono<Void> deleteByIdUsuarioAndTipoUsuario(Integer idUsuario, String tipoUsuario);
}
