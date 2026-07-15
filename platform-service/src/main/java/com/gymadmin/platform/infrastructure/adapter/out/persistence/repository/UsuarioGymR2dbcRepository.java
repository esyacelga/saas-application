package com.gymadmin.platform.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.UsuarioGymEntity;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface UsuarioGymR2dbcRepository extends ReactiveCrudRepository<UsuarioGymEntity, Long> {

    Mono<UsuarioGymEntity> findByIdCompaniaAndCorreoAndEliminadoFalse(Long idCompania, String correo);

    // Verificación GLOBAL de correo (no por compañía): el correo es la llave de login,
    // así que en el auto-registro público —donde la compañía aún no existe— hay que
    // comprobar que ningún otro usuario del sistema lo tenga ya.
    Mono<UsuarioGymEntity> findFirstByCorreoAndEliminadoFalse(String correo);
}
