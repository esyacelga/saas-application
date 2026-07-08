package com.gymadmin.auth.application.service;

import com.gymadmin.auth.domain.exception.ConflictException;
import com.gymadmin.auth.domain.exception.ResourceNotFoundException;
import com.gymadmin.auth.domain.port.in.PlatformRolUseCase;
import com.gymadmin.auth.domain.port.out.PlatformRolPort;
import com.gymadmin.auth.domain.port.out.RolPermisoPort;
import com.gymadmin.auth.dto.request.AsignarPermisoRolRequest;
import com.gymadmin.auth.dto.request.CreatePlatformRolRequest;
import com.gymadmin.auth.dto.request.UpdatePlatformRolRequest;
import com.gymadmin.auth.dto.request.UpdateRolPermisosRequest;
import com.gymadmin.auth.dto.response.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class PlatformRolApplicationService implements PlatformRolUseCase {

    private final PlatformRolPort platformRolPort;
    private final RolPermisoPort rolPermisoPort;

    @Override
    public Flux<RolPlataformaResponse> listarRoles() {
        return platformRolPort.findAllRoles();
    }

    @Override
    public Mono<RolConPermisosPlataformaResponse> verPermisosPorRol(Integer id) {
        return platformRolPort.findRolById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Rol no encontrado: " + id)))
                .flatMap(rol -> rolPermisoPort.findPermisosWithDetailByIdRol(id)
                        .map(p -> new PermisoResponse(p.getId(), p.getNombre(), p.getModulo(), p.getDescripcion()))
                        .collectList()
                        .map(permisos -> new RolConPermisosPlataformaResponse(rol, permisos)));
    }

    @Override
    public Flux<CompaniaBasicaResponse> listarCompanias() {
        return platformRolPort.findAllCompanias();
    }

    @Override
    public Flux<SucursalBasicaResponse> listarSucursales(Integer idCompania) {
        return platformRolPort.existsCompania(idCompania)
                .flatMapMany(exists -> {
                    if (!exists) {
                        return Flux.error(new ResourceNotFoundException("Compañía no encontrada"));
                    }
                    return platformRolPort.findSucursalesByCompania(idCompania);
                });
    }

    @Override
    @Transactional
    public Mono<RolPlataformaResponse> crearRol(CreatePlatformRolRequest req, String createdBy) {
        return platformRolPort.save(req.idCompania(), req.nombre(), req.descripcion(), createdBy, req.idSucursal());
    }

    @Override
    @Transactional
    public Mono<RolPlataformaResponse> actualizarRol(Integer id, UpdatePlatformRolRequest req, String updatedBy) {
        return platformRolPort.findRolById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Rol no encontrado: " + id)))
                .then(platformRolPort.update(id, req.nombre(), req.descripcion(), updatedBy));
    }

    @Override
    @Transactional
    public Mono<Void> eliminarRol(Integer id) {
        return platformRolPort.findRolById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Rol no encontrado: " + id)))
                .then(platformRolPort.countUsuariosByRolId(id))
                .flatMap(count -> {
                    if (count > 0)
                        return Mono.error(new ConflictException(
                                "No se puede eliminar el rol: tiene " + count + " usuario(s) asignado(s)"));
                    return rolPermisoPort.deleteByIdRol(id)
                            .then(platformRolPort.deleteById(id));
                });
    }

    @Override
    @Transactional
    public Mono<Void> reemplazarPermisos(Integer id, UpdateRolPermisosRequest req, String updatedBy) {
        return platformRolPort.findRolById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Rol no encontrado: " + id)))
                .then(rolPermisoPort.deleteByIdRol(id))
                .then(rolPermisoPort.saveAll(id, req.idPermisos(), updatedBy));
    }

    @Override
    public Flux<PermisoRolResponse> verPermisosDetalle(Integer idRol) {
        return platformRolPort.findRolById(idRol)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Rol no encontrado: " + idRol)))
                .flatMapMany(rol -> rolPermisoPort.findPermisosConSucursalByIdRol(idRol));
    }

    @Override
    @Transactional
    public Mono<Void> asignarPermiso(Integer idRol, AsignarPermisoRolRequest req, String createdBy) {
        return platformRolPort.findRolById(idRol)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Rol no encontrado: " + idRol)))
                .then(rolPermisoPort.asignar(idRol, req.idPermiso(), createdBy));
    }

    @Override
    @Transactional
    public Mono<Void> eliminarPermisoDeRol(Integer idRol, Integer idPermiso, String updatedBy) {
        return platformRolPort.findRolById(idRol)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Rol no encontrado: " + idRol)))
                .then(rolPermisoPort.softDeleteAsignacion(idRol, idPermiso, updatedBy));
    }
}
