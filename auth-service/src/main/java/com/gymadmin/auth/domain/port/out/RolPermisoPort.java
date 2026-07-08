package com.gymadmin.auth.domain.port.out;

import com.gymadmin.auth.domain.model.Permiso;
import com.gymadmin.auth.dto.response.PermisoRolResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface RolPermisoPort {
    Flux<String> findNombresPermisoByIdRol(Integer idRol);
    Flux<Permiso> findPermisosWithDetailByIdRol(Integer idRol);
    Mono<Void> deleteByIdRol(Integer idRol);
    Mono<Void> saveAll(Integer idRol, Iterable<Integer> idPermisos, String createdBy);

    // Granular CRUD with soft-delete
    Flux<PermisoRolResponse> findPermisosConSucursalByIdRol(Integer idRol);
    Mono<Void> asignar(Integer idRol, Integer idPermiso, String createdBy);
    Mono<Void> softDeleteAsignacion(Integer idRol, Integer idPermiso, String updatedBy);
}
