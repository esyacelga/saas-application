package com.gymadmin.core.domain.port.out;

import reactor.core.publisher.Mono;

public interface PersonaRepository {

    Mono<PersonaResult> findByCi(String ci);

    Mono<PersonaResult> create(CreatePersonaCommand command);

    record PersonaResult(Long id, String ci, String nombre, String telefono, String correo, String fotoUrl) {}

    record CreatePersonaCommand(
        String ci,
        String nombre,
        String telefono,
        String correo,
        java.time.LocalDate fechaNacimiento,
        String fotoUrl
    ) {}
}
