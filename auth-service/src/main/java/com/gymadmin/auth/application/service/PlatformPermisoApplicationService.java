package com.gymadmin.auth.application.service;

import com.gymadmin.auth.domain.exception.ConflictException;
import com.gymadmin.auth.domain.exception.ResourceNotFoundException;
import com.gymadmin.auth.domain.port.in.PlatformPermisoUseCase;
import com.gymadmin.auth.domain.port.out.PlatformPermisoPort;
import com.gymadmin.auth.dto.request.CreatePermisoRequest;
import com.gymadmin.auth.dto.request.UpdatePermisoRequest;
import com.gymadmin.auth.dto.response.PermisoPlataformaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class PlatformPermisoApplicationService implements PlatformPermisoUseCase {

    private final PlatformPermisoPort platformPermisoPort;

    @Override
    public Flux<PermisoPlataformaResponse> listarTodos() {
        return platformPermisoPort.findAllWithSucursal();
    }

    @Override
    @Transactional
    public Mono<PermisoPlataformaResponse> crear(CreatePermisoRequest req, String createdBy) {
        return platformPermisoPort.existsByIdCompaniaAndNombreAndNotDeleted(req.idCompania(), req.nombre())
                .flatMap(exists -> exists
                        ? Mono.error(new ConflictException("Ya existe un permiso con ese nombre en la compañía"))
                        : platformPermisoPort.create(req, createdBy));
    }

    @Override
    @Transactional
    public Mono<PermisoPlataformaResponse> actualizar(Integer id, UpdatePermisoRequest req, String updatedBy) {
        return platformPermisoPort.findByIdWithSucursal(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Permiso no encontrado: " + id)))
                .flatMap(existing -> {
                    if (req.nombre() != null && !req.nombre().equals(existing.nombre())) {
                        return platformPermisoPort.existsByIdCompaniaAndNombreAndNotDeleted(existing.idCompania(), req.nombre())
                                .flatMap(exists -> exists
                                        ? Mono.error(new ConflictException("Ya existe un permiso con ese nombre en la compañía"))
                                        : platformPermisoPort.update(id, req, updatedBy));
                    }
                    return platformPermisoPort.update(id, req, updatedBy);
                });
    }

    @Override
    @Transactional
    public Mono<Void> eliminar(Integer id, String updatedBy) {
        return platformPermisoPort.findByIdWithSucursal(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Permiso no encontrado: " + id)))
                .then(platformPermisoPort.softDelete(id, updatedBy));
    }
}
