package com.gymadmin.auth.application.service;

import com.gymadmin.auth.domain.port.in.PermisoUseCase;
import com.gymadmin.auth.domain.port.out.PermisoPort;
import com.gymadmin.auth.domain.port.out.RolPermisoPort;
import com.gymadmin.auth.dto.response.PermisoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class PermisoApplicationService implements PermisoUseCase {

    private final PermisoPort permisoPort;
    private final RolPermisoPort rolPermisoPort;

    @Override
    public Flux<PermisoResponse> listarPorCompania(Integer idCompania) {
        return permisoPort.findByIdCompania(idCompania)
                .map(this::toResponse);
    }

    @Override
    public Flux<PermisoResponse> listarPorRol(Integer idRol, Integer idCompania) {
        return rolPermisoPort.findPermisosWithDetailByIdRol(idRol)
                .filter(p -> idCompania.equals(p.getIdCompania()))
                .map(this::toResponse);
    }

    private PermisoResponse toResponse(com.gymadmin.auth.domain.model.Permiso p) {
        return new PermisoResponse(p.getId(), p.getNombre(), p.getModulo(), p.getDescripcion());
    }
}
