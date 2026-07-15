package com.gymadmin.platform.domain.port.out;

import reactor.core.publisher.Mono;

public interface UsuarioGymRepository {

    record UsuarioCreado(Long id, Long idPersona, String correo) {}

    Mono<UsuarioCreado> crearUsuario(
            Long idCompania,
            Long idSucursal,
            Long idRol,
            Long idPersona,
            String correo,
            String passwordHash
    );

    Mono<Boolean> existeCorreo(Long idCompania, String correo);

    // Verificación global de correo (llave de login), independiente de la compañía.
    Mono<Boolean> existeCorreoGlobal(String correo);
}
