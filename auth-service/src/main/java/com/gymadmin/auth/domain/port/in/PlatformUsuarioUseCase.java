package com.gymadmin.auth.domain.port.in;

import com.gymadmin.auth.dto.request.CreatePlatformUsuarioRequest;
import com.gymadmin.auth.dto.response.PlatformUsuarioResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PlatformUsuarioUseCase {
    Flux<PlatformUsuarioResponse> listar();
    Flux<PlatformUsuarioResponse> listarPorPersona(Integer idPersona);
    Mono<PlatformUsuarioResponse> crear(CreatePlatformUsuarioRequest req, String createdBy);
    Mono<PlatformUsuarioResponse> actualizar(Integer id, String nuevoRol, String updatedBy);
    Mono<PlatformUsuarioResponse> actualizarFoto(Integer id, String fotoUrl, String updatedBy);
    Mono<Void> desactivar(Integer id, String updatedBy);
}
