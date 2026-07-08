package com.gymadmin.platform.domain.port.out;

import reactor.core.publisher.Mono;

public interface PersonaRepository {

    /**
     * Resolves the persona ID for a wizard user.
     * - If idPersona is provided, returns it directly (persona already exists).
     * - Otherwise, looks up by CI; if found, returns its ID.
     * - If not found, creates a new persona and returns the new ID.
     */
    Mono<Long> resolverIdPersona(Long idPersona, String ci, String nombre, String correo, String telefono);
}
