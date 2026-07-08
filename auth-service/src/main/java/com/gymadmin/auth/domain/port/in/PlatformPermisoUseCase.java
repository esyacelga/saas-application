package com.gymadmin.auth.domain.port.in;

import com.gymadmin.auth.dto.request.CreatePermisoRequest;
import com.gymadmin.auth.dto.request.UpdatePermisoRequest;
import com.gymadmin.auth.dto.response.PermisoPlataformaResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PlatformPermisoUseCase {
    Flux<PermisoPlataformaResponse> listarTodos();
    Mono<PermisoPlataformaResponse> crear(CreatePermisoRequest req, String createdBy);
    Mono<PermisoPlataformaResponse> actualizar(Integer id, UpdatePermisoRequest req, String updatedBy);
    Mono<Void> eliminar(Integer id, String updatedBy);
}
