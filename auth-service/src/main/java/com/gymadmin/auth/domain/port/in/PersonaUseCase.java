package com.gymadmin.auth.domain.port.in;

import com.gymadmin.auth.dto.request.CreatePersonaRequest;
import com.gymadmin.auth.dto.request.UpdatePersonaRequest;
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
}
