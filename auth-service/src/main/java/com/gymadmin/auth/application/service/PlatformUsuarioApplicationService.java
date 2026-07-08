package com.gymadmin.auth.application.service;

import com.gymadmin.auth.domain.exception.ConflictException;
import com.gymadmin.auth.domain.exception.ResourceNotFoundException;
import com.gymadmin.auth.domain.model.UsuarioPlataforma;
import com.gymadmin.auth.domain.port.in.PlatformUsuarioUseCase;
import com.gymadmin.auth.domain.port.out.PersonaPort;
import com.gymadmin.auth.domain.port.out.UsuarioPlataformaPort;
import com.gymadmin.auth.dto.request.CreatePlatformUsuarioRequest;
import com.gymadmin.auth.dto.response.PlatformUsuarioResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class PlatformUsuarioApplicationService implements PlatformUsuarioUseCase {

    private final UsuarioPlataformaPort plataformaPort;
    private final PersonaPort personaPort;
    private final PasswordEncoder encoder;

    private PlatformUsuarioResponse toResponse(UsuarioPlataforma u) {
        return new PlatformUsuarioResponse(
                u.getId(), u.getNombrePersona(), u.getCorreo(),
                u.getRol(), u.getActivo(), u.getUltimoAcceso(), u.getFotoUrlPersona()
        );
    }

    @Override
    public Flux<PlatformUsuarioResponse> listar() {
        return plataformaPort.findAll().map(this::toResponse);
    }

    @Override
    @Transactional
    public Mono<PlatformUsuarioResponse> crear(CreatePlatformUsuarioRequest req, String createdBy) {
        return plataformaPort.existsByCorreo(req.correo())
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists))
                        return Mono.error(new ConflictException("El correo ya está registrado"));
                    return personaPort.findById(req.idPersona())
                            .switchIfEmpty(Mono.error(new ResourceNotFoundException("Persona no encontrada: " + req.idPersona())))
                            .flatMap(persona -> {
                                UsuarioPlataforma u = UsuarioPlataforma.builder()
                                        .idPersona(persona.getId()).correo(req.correo())
                                        .passwordHash(encoder.encode(req.password()))
                                        .rol(req.rol()).activo(true)
                                        .creacionUsuario(createdBy)
                                        .build();
                                return plataformaPort.save(u);
                            });
                })
                .map(this::toResponse);
    }

    @Override
    @Transactional
    public Mono<PlatformUsuarioResponse> actualizar(Integer id, String nuevoRol, String updatedBy) {
        return plataformaPort.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Operador no encontrado: " + id)))
                .flatMap(u -> {
                    u.setRol(nuevoRol);
                    u.setModificaUsuario(updatedBy);
                    return plataformaPort.save(u);
                })
                .map(this::toResponse);
    }

    @Override
    @Transactional
    public Mono<PlatformUsuarioResponse> actualizarFoto(Integer id, String fotoUrl, String updatedBy) {
        return plataformaPort.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Operador no encontrado: " + id)))
                .flatMap(u -> personaPort.findById(u.getIdPersona())
                        .switchIfEmpty(Mono.error(new ResourceNotFoundException("Persona no encontrada: " + u.getIdPersona())))
                        .flatMap(persona -> {
                            persona.setFotoUrl(fotoUrl);
                            persona.setModificaUsuario(updatedBy);
                            return personaPort.save(persona);
                        })
                        .thenReturn(u.getId()))
                .flatMap(plataformaPort::findById)
                .map(this::toResponse);
    }

    @Override
    @Transactional
    public Mono<Void> desactivar(Integer id, String updatedBy) {
        return plataformaPort.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Operador no encontrado: " + id)))
                .flatMap(u -> {
                    if ("super_admin".equals(u.getRol()))
                        return plataformaPort.countByRolAndActivoTrue("super_admin")
                                .flatMap(count -> count <= 1
                                        ? Mono.error(new ConflictException("No se puede desactivar al último super_admin activo"))
                                        : Mono.just(u));
                    return Mono.just(u);
                })
                .flatMap(u -> {
                    u.setActivo(false);
                    u.setModificaUsuario(updatedBy);
                    return plataformaPort.save(u);
                })
                .then();
    }

    @Override
    public Flux<PlatformUsuarioResponse> listarPorPersona(Integer idPersona) {
        return plataformaPort.findByIdPersona(idPersona).map(this::toResponse);
    }
}
