package com.gymadmin.auth.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.auth.infrastructure.adapter.out.persistence.entity.UsuarioStaffEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface UsuarioStaffR2dbcRepository extends ReactiveCrudRepository<UsuarioStaffEntity, Integer> {
    Mono<UsuarioStaffEntity> findByCorreoAndIdCompania(String correo, Integer idCompania);
    Mono<Boolean> existsByCorreoAndIdCompania(String correo, Integer idCompania);
    Flux<UsuarioStaffEntity> findByIdCompania(Integer idCompania);
    Mono<UsuarioStaffEntity> findByIdAndIdCompania(Integer id, Integer idCompania);
    Flux<UsuarioStaffEntity> findByIdPersona(Integer idPersona);

    @Query("""
            SELECT COUNT(*) FROM seguridad.usuarios u
            JOIN seguridad.roles r ON u.id_rol = r.id
            WHERE u.id_compania = :idCompania AND r.nombre = 'Dueño' AND u.activo = true
            """)
    Mono<Long> countActiveDuenos(Integer idCompania);

    @Query("SELECT COUNT(*) > 0 FROM seguridad.usuarios WHERE id_rol = :idRol AND id_compania = :idCompania")
    Mono<Boolean> existsByIdRolAndIdCompania(Integer idRol, Integer idCompania);
}
