package com.gymadmin.auth.application.service;

import com.gymadmin.auth.domain.exception.ConflictException;
import com.gymadmin.auth.domain.exception.ResourceNotFoundException;
import com.gymadmin.auth.domain.model.Rol;
import com.gymadmin.auth.domain.port.in.RolUseCase;
import com.gymadmin.auth.domain.port.out.PermisoPort;
import com.gymadmin.auth.domain.port.out.RolPermisoPort;
import com.gymadmin.auth.domain.port.out.RolPort;
import com.gymadmin.auth.domain.port.out.UsuarioStaffPort;
import com.gymadmin.auth.dto.request.CreateRolRequest;
import com.gymadmin.auth.dto.request.UpdateRolPermisosRequest;
import com.gymadmin.auth.dto.response.PermisoResponse;
import com.gymadmin.auth.dto.response.RolConPermisosResponse;
import com.gymadmin.auth.dto.response.RolResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class RolApplicationService implements RolUseCase {

    private final RolPort rolPort;
    private final PermisoPort permisoPort;
    private final RolPermisoPort rolPermisoPort;
    private final UsuarioStaffPort staffPort;

    @Override
    public Flux<RolResponse> listarPorCompania(Integer idCompania) {
        return rolPort.findByIdCompania(idCompania)
                .map(r -> new RolResponse(r.getId(), r.getNombre(), r.getDescripcion()));
    }

    @Override
    public Mono<RolResponse> buscarPorId(Integer id, Integer idCompania) {
        return rolPort.findByIdAndIdCompania(id, idCompania)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Rol no encontrado: " + id)))
                .map(r -> new RolResponse(r.getId(), r.getNombre(), r.getDescripcion()));
    }

    @Override
    @Transactional
    public Mono<RolResponse> crear(Integer idCompania, Integer idSucursal, CreateRolRequest req, String createdBy) {
        return rolPort.existsByIdCompaniaAndNombre(idCompania, req.nombre())
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists))
                        return Mono.error(new ConflictException(
                                "Ya existe un rol con nombre '" + req.nombre() + "' en esta compañía"));
                    Rol rol = Rol.builder()
                            .idCompania(idCompania).idSucursal(idSucursal)
                            .nombre(req.nombre()).descripcion(req.descripcion())
                            .creacionUsuario(createdBy)
                            .build();
                    return rolPort.save(rol);
                })
                .map(r -> new RolResponse(r.getId(), r.getNombre(), r.getDescripcion()));
    }

    @Override
    public Mono<RolConPermisosResponse> verPermisos(Integer id, Integer idCompania) {
        return rolPort.findByIdAndIdCompania(id, idCompania)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Rol no encontrado: " + id)))
                .flatMap(rol -> rolPermisoPort.findPermisosWithDetailByIdRol(id)
                        .map(p -> new PermisoResponse(p.getId(), p.getNombre(), p.getModulo(), p.getDescripcion()))
                        .collectList()
                        .map(permisos -> new RolConPermisosResponse(
                                new RolResponse(rol.getId(), rol.getNombre(), rol.getDescripcion()), permisos)));
    }

    @Override
    @Transactional
    public Mono<Void> actualizarPermisos(Integer id, Integer idCompania, UpdateRolPermisosRequest req, String updatedBy) {
        return rolPort.findByIdAndIdCompania(id, idCompania)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Rol no encontrado: " + id)))
                // Mono.defer: sin él la consulta de permisos se lanza aunque el rol no exista
                // y el flujo vaya a terminar en ResourceNotFoundException.
                .then(Mono.defer(() -> permisoPort.findByIdInAndIdCompania(req.idPermisos(), idCompania).collectList()))
                .flatMap(permisos -> {
                    if (permisos.size() != req.idPermisos().size())
                        return Mono.error(new IllegalArgumentException(
                                "Uno o más permisos no pertenecen a esta compañía"));
                    return rolPermisoPort.deleteByIdRol(id)
                            .then(rolPermisoPort.saveAll(id, req.idPermisos(), updatedBy));
                });
    }

    @Override
    @Transactional
    public Mono<Void> eliminar(Integer id, Integer idCompania) {
        return rolPort.findByIdAndIdCompania(id, idCompania)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Rol no encontrado: " + id)))
                .then(Mono.defer(() -> staffPort.existsByIdRolInCompania(id, idCompania)))
                .flatMap(hasUsers -> {
                    if (Boolean.TRUE.equals(hasUsers))
                        return Mono.error(new ConflictException("No se puede eliminar el rol: tiene usuarios asignados"));
                    return rolPermisoPort.deleteByIdRol(id)
                            .then(rolPort.deleteById(id));
                });
    }
}
