package com.gymadmin.auth.domain.port.out;

import com.gymadmin.auth.domain.model.Persona;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

public interface PersonaPort {
    Mono<Persona> findByCi(String ci);
    Mono<Boolean> existsByCi(String ci);
    Mono<Boolean> existsByCiAndIdNot(String ci, Integer id);
    Mono<Persona> findById(Integer id);
    Mono<Persona> save(Persona persona);
    Mono<Persona> findByCorreo(String correo);
    Flux<Persona> findAll(String nombre, String ci, String correo, String sexo, int offset, int limit);
    Mono<Long> countAll(String nombre, String ci, String correo, String sexo);

    /**
     * Fase 6 (bloque E): actualiza SOLO el opt-in de WhatsApp del socio ({@code acepta_whatsapp} +
     * {@code fecha_consentimiento_wa}), sin tocar el resto de campos. El {@code save()} normal (vía
     * {@code PersonaMapper}) deliberadamente no persiste el opt-in, así que este es el único punto de
     * escritura del consentimiento. Emite el número de filas afectadas (0 si la persona no existe).
     */
    Mono<Long> updateConsentimientoWa(Integer id, boolean acepta, OffsetDateTime fecha);
}
