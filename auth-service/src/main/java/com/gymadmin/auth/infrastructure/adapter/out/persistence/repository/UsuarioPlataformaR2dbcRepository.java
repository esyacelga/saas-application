package com.gymadmin.auth.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.UsuarioPlataformaEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UsuarioPlataformaR2dbcRepository extends ReactiveCrudRepository<UsuarioPlataformaEntity, Integer> {
    Mono<UsuarioPlataformaEntity> findByCorreo(String correo);
    Mono<Boolean> existsByCorreo(String correo);
    Flux<UsuarioPlataformaEntity> findByIdPersona(Integer idPersona);

    @Query("SELECT COUNT(*) FROM saas.usuarios_plataforma WHERE rol = :rol AND activo = true")
    Mono<Long> countByRolAndActivoTrue(String rol);
}
