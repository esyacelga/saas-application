package com.gymadmin.auth.domain.port.out;

import com.gymadmin.auth.dto.request.CreatePermisoRequest;
import com.gymadmin.auth.dto.request.UpdatePermisoRequest;
import com.gymadmin.auth.dto.response.PermisoPlataformaResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PlatformPermisoPort {
    Flux<PermisoPlataformaResponse> findAllWithSucursal();
    Mono<PermisoPlataformaResponse> findByIdWithSucursal(Integer id);
    Mono<Boolean> existsByIdCompaniaAndNombreAndNotDeleted(Integer idCompania, String nombre);
    Mono<PermisoPlataformaResponse> create(CreatePermisoRequest req, String createdBy);
    Mono<PermisoPlataformaResponse> update(Integer id, UpdatePermisoRequest req, String updatedBy);
    Mono<Void> softDelete(Integer id, String updatedBy);
}
