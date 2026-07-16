package com.gymadmin.auth.domain.port.in;

import com.gymadmin.auth.dto.request.CreatePersonaRequest;
import com.gymadmin.auth.dto.request.UpdatePersonaRequest;
import com.gymadmin.auth.dto.response.ConsentimientoWaResponse;
import com.gymadmin.auth.dto.response.PersonaPageResponse;
import com.gymadmin.auth.dto.response.PersonaResponse;
import reactor.core.publisher.Mono;

public interface PersonaUseCase {
    Mono<PersonaResponse> findById(Integer id);
    Mono<PersonaResponse> findByCi(String ci);
    Mono<PersonaResponse> findByCorreo(String correo);
    Mono<PersonaResponse> create(CreatePersonaRequest req, String createdBy);
    Mono<PersonaResponse> update(Integer id, UpdatePersonaRequest req, String updatedBy);
    Mono<PersonaPageResponse> listar(String nombre, String ci, String correo, String sexo, int page, int size);

    /**
     * Fase 6 (bloque E): registra el opt-in/opt-out de WhatsApp del socio. {@code acepta=true} sella
     * {@code fecha_consentimiento_wa}; {@code acepta=false} (opt-out) la limpia. Se captura desde el
     * registro público, recepción o el perfil PWA del socio.
     */
    Mono<ConsentimientoWaResponse> actualizarConsentimientoWa(Integer id, boolean acepta);
}
