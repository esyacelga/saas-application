package com.gymadmin.core.domain.port.out;

import reactor.core.publisher.Mono;

public interface PersonaRepository {

    Mono<PersonaResult> findByCi(String ci);

    Mono<String> findNombreById(Long id);

    Mono<PersonaResult> create(CreatePersonaCommand command);

    /**
     * Registra el opt-in de WhatsApp de una persona que YA existe (se afilia a un
     * segundo gym). Solo hace UPDATE cuando {@code acepta_whatsapp = false}: si la
     * persona ya consintió antes, se conserva su {@code fecha_consentimiento_wa}
     * original — es la prueba del opt-in ante Meta y no debe reescribirse.
     *
     * <p>Nunca revoca: la recepción no puede dar de baja un consentimiento que el
     * socio otorgó. El opt-out es exclusivo del propio socio desde su perfil PWA
     * ({@code PATCH /personas/{id}/consentimiento-wa} en auth-service).
     */
    Mono<Void> otorgarConsentimientoWa(Long idPersona);

    record PersonaResult(Long id, String ci, String nombre, String telefono, String correo, String fotoUrl) {}

    record CreatePersonaCommand(
        String ci,
        String nombre,
        String telefono,
        String correo,
        java.time.LocalDate fechaNacimiento,
        String fotoUrl,
        boolean aceptaWhatsapp
    ) {}
}
