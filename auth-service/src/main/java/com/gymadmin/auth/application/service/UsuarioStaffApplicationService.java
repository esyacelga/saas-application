package com.gymadmin.auth.application.service;

import com.gymadmin.auth.domain.exception.BadRequestException;
import com.gymadmin.auth.domain.exception.ConflictException;
import com.gymadmin.auth.domain.exception.ResourceNotFoundException;
import com.gymadmin.auth.domain.model.UsuarioStaff;
import com.gymadmin.auth.domain.port.in.UsuarioStaffUseCase;
import com.gymadmin.auth.domain.port.out.PersonaPort;
import com.gymadmin.auth.domain.port.out.RolPermisoPort;
import com.gymadmin.auth.domain.port.out.RolPort;
import com.gymadmin.auth.domain.port.out.UsuarioStaffPort;
import com.gymadmin.auth.dto.request.CreateUsuarioStaffRequest;
import com.gymadmin.auth.dto.request.UpdateUsuarioStaffRequest;
import com.gymadmin.auth.dto.response.RolResponse;
import com.gymadmin.auth.dto.response.UsuarioPermisosResponse;
import com.gymadmin.auth.dto.response.UsuarioStaffResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UsuarioStaffApplicationService implements UsuarioStaffUseCase {

    private final UsuarioStaffPort staffPort;
    private final RolPort rolPort;
    private final RolPermisoPort rolPermisoPort;
    private final PersonaPort personaPort;
    private final PasswordEncoder encoder;

    @Override
    public Flux<UsuarioStaffResponse> listar(Integer idCompania) {
        return staffPort.findByIdCompania(idCompania).map(this::toResponse);
    }

    @Override
    @Transactional
    public Mono<UsuarioStaffResponse> crear(Integer idCompania, Integer idSucursal, CreateUsuarioStaffRequest req, String createdBy) {
        return personaPort.findById(req.idPersona())
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Persona no encontrada: " + req.idPersona())))
                .flatMap(persona -> staffPort.existsByCorreoAndIdCompania(req.correo(), idCompania)
                        .flatMap(exists -> {
                            if (Boolean.TRUE.equals(exists))
                                return Mono.error(new ConflictException("El correo ya está registrado en esta compañía"));
                            return rolPort.findByIdAndIdCompania(req.idRol(), idCompania)
                                    .switchIfEmpty(Mono.error(new BadRequestException("El rol no pertenece a esta compañía")));
                        })
                        .flatMap(rol -> {
                            UsuarioStaff u = UsuarioStaff.builder()
                                    .idCompania(idCompania).idSucursal(idSucursal)
                                    .idRol(rol.getId()).nombreRol(rol.getNombre())
                                    .idPersona(req.idPersona()).correo(req.correo())
                                    .passwordHash(encoder.encode(req.passwordTemporal()))
                                    .requiereCambioPwd(true).activo(true)
                                    .creacionUsuario(createdBy)
                                    .build();
                            return staffPort.save(u);
                        }))
                .map(this::toResponse);
    }

    @Override
    @Transactional
    public Mono<UsuarioStaffResponse> editar(Integer id, Integer idCompania, UpdateUsuarioStaffRequest req, String updatedBy) {
        return staffPort.findByIdAndIdCompania(id, idCompania)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Usuario no encontrado: " + id)))
                .flatMap(u -> checkCorreoConflict(u, req.correo(), idCompania))
                .flatMap(u -> resolveRol(u, req.idRol(), idCompania))
                .flatMap(u -> {
                    if (req.correo() != null) u.setCorreo(req.correo());
                    u.setModificaUsuario(updatedBy);
                    return staffPort.save(u);
                })
                .map(this::toResponse);
    }

    private Mono<UsuarioStaff> checkCorreoConflict(UsuarioStaff u, String nuevoCorreo, Integer idCompania) {
        if (nuevoCorreo == null || nuevoCorreo.equals(u.getCorreo())) return Mono.just(u);
        return staffPort.existsByCorreoAndIdCompania(nuevoCorreo, idCompania)
                .flatMap(exists -> Boolean.TRUE.equals(exists)
                        ? Mono.error(new ConflictException("El correo ya está registrado en esta compañía"))
                        : Mono.just(u));
    }

    private Mono<UsuarioStaff> resolveRol(UsuarioStaff u, Integer idRol, Integer idCompania) {
        if (idRol == null) return Mono.just(u);
        return rolPort.findByIdAndIdCompania(idRol, idCompania)
                .switchIfEmpty(Mono.error(new BadRequestException("El rol no pertenece a esta compañía")))
                .map(rol -> {
                    u.setIdRol(rol.getId());
                    u.setNombreRol(rol.getNombre());
                    return u;
                });
    }

    @Override
    public Mono<UsuarioPermisosResponse> verPermisos(Integer id, Integer idCompania) {
        return staffPort.findByIdAndIdCompania(id, idCompania)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Usuario no encontrado: " + id)))
                .flatMap(u -> {
                    Mono<java.util.List<String>> permisosMono = u.getIdRol() != null
                            ? rolPermisoPort.findNombresPermisoByIdRol(u.getIdRol()).collectList()
                            : Mono.just(java.util.List.<String>of());

                    RolResponse rolResp = u.getIdRol() != null
                            ? new RolResponse(u.getIdRol(), u.getNombreRol(), null) : null;

                    return permisosMono.map(permisos ->
                            new UsuarioPermisosResponse(
                                    new UsuarioPermisosResponse.UsuarioInfo(u.getId(), u.getNombrePersona()),
                                    rolResp, permisos));
                });
    }

    @Override
    @Transactional
    public Mono<Void> desactivar(Integer id, Integer idCompania, String updatedBy) {
        return staffPort.findByIdAndIdCompania(id, idCompania)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Usuario no encontrado: " + id)))
                .flatMap(u -> {
                    if ("Dueño".equals(u.getNombreRol()))
                        return staffPort.countActiveDuenos(idCompania)
                                .flatMap(count -> count <= 1
                                        ? Mono.error(new ConflictException("No se puede desactivar al único dueño activo"))
                                        : Mono.just(u));
                    return Mono.just(u);
                })
                .flatMap(u -> {
                    u.setActivo(false);
                    u.setModificaUsuario(updatedBy);
                    return staffPort.save(u);
                })
                .then();
    }

    @Override
    @Transactional
    public Mono<Void> resetPassword(Integer id, Integer idCompania, String newPassword, String updatedBy) {
        return staffPort.findByIdAndIdCompania(id, idCompania)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Usuario no encontrado: " + id)))
                .flatMap(u -> {
                    u.setPasswordHash(encoder.encode(newPassword));
                    u.setRequiereCambioPwd(false);
                    u.setModificaUsuario(updatedBy);
                    return staffPort.save(u);
                })
                .then();
    }

    @Override
    @Transactional
    public Mono<Void> activar(Integer id, Integer idCompania, String updatedBy) {
        return staffPort.findByIdAndIdCompania(id, idCompania)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Usuario no encontrado: " + id)))
                .flatMap(u -> {
                    u.setActivo(true);
                    u.setModificaUsuario(updatedBy);
                    return staffPort.save(u);
                })
                .then();
    }

    @Override
    public Flux<UsuarioStaffResponse> listarPorPersona(Integer idPersona) {
        return staffPort.findByIdPersona(idPersona).map(this::toResponse);
    }

    private UsuarioStaffResponse toResponse(UsuarioStaff u) {
        return new UsuarioStaffResponse(u.getId(), u.getIdPersona(), u.getNombrePersona(),
                u.getCorreo(), u.getFotoUrlPersona(),
                u.getIdRol(), u.getNombreRol(), u.getActivo(), u.getUltimoAcceso());
    }
}
