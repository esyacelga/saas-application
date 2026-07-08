package com.gymadmin.platform.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.RolPermisoEntity;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface RolPermisoR2dbcRepository extends ReactiveCrudRepository<RolPermisoEntity, Long> {

    @Modifying
    @Query("INSERT INTO seguridad.rol_permisos (id_rol, id_permiso, creacion_usuario) " +
           "SELECT :idRol, id, 'sistema' FROM seguridad.permisos " +
           "WHERE id_compania = :idCompania AND eliminado = false " +
           "ON CONFLICT DO NOTHING")
    Mono<Void> asignarTodosLosPermisos(Long idRol, Long idCompania);
}
