package com.gymadmin.platform.domain.port.out;

import reactor.core.publisher.Mono;

public interface RolGymRepository {

    /** Crea el rol SUPER_ADMIN para la compañía/sucursal dada. Devuelve el id del rol creado. */
    Mono<Long> crearSuperAdmin(Long idCompania, Long idSucursal);

    /** Inserta los permisos base y los asigna al rol en una sola operación. */
    Mono<Void> crearPermisosYAsignar(Long idRol, Long idCompania, Long idSucursal);
}
