package com.gymadmin.auth.domain.port.in;

import com.gymadmin.auth.dto.request.CreateAppUsuarioRequest;
import com.gymadmin.auth.dto.request.UpdateAppUsuarioRequest;
import com.gymadmin.auth.dto.response.AppUsuarioResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AppUsuarioUseCase {
    Mono<Void> crear(Integer idCompania, CreateAppUsuarioRequest req, String createdBy);
    Mono<Void> desactivar(Integer id, String updatedBy);
    Mono<Void> activar(Integer id, String updatedBy);
    Mono<AppUsuarioResponse> obtenerPorCi(String ci, Integer idCompania);
    Mono<Void> actualizar(Integer id, UpdateAppUsuarioRequest req, String updatedBy);
    Flux<AppUsuarioResponse> listarPorPersona(Integer idPersona);
}
