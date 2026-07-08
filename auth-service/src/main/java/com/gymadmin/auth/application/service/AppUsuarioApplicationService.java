package com.gymadmin.auth.application.service;

import com.gymadmin.auth.domain.exception.ConflictException;
import com.gymadmin.auth.domain.exception.ResourceNotFoundException;
import com.gymadmin.auth.domain.model.UsuarioApp;
import com.gymadmin.auth.domain.port.in.AppUsuarioUseCase;
import com.gymadmin.auth.domain.port.out.PersonaPort;
import com.gymadmin.auth.domain.port.out.UsuarioAppPort;
import com.gymadmin.auth.dto.request.CreateAppUsuarioRequest;
import com.gymadmin.auth.dto.request.UpdateAppUsuarioRequest;
import com.gymadmin.auth.dto.response.AppUsuarioResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AppUsuarioApplicationService implements AppUsuarioUseCase {

    private final UsuarioAppPort usuarioAppPort;
    private final PersonaPort personaPort;
    private final PasswordEncoder encoder;

    @Override
    @Transactional
    public Mono<Void> crear(Integer idCompania, CreateAppUsuarioRequest req, String createdBy) {
        return personaPort.findById(req.idPersona())
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Persona no encontrada: " + req.idPersona())))
                .flatMap(persona -> usuarioAppPort.existsByIdPersonaAndIdCompania(persona.getId(), idCompania)
                        .flatMap(exists -> {
                            if (Boolean.TRUE.equals(exists))
                                return Mono.error(new ConflictException("La persona ya tiene una cuenta app en esta compañía"));
                            UsuarioApp u = UsuarioApp.builder()
                                    .idPersona(persona.getId())
                                    .nombrePersona(persona.getNombre())
                                    .idCompania(idCompania)
                                    .login(req.login())
                                    .passwordHash(encoder.encode(req.password()))
                                    .requiereCambioPwd(true).activo(true)
                                    .creacionUsuario(createdBy)
                                    .build();
                            return usuarioAppPort.save(u);
                        }))
                .then();
    }

    @Override
    @Transactional
    public Mono<Void> desactivar(Integer id, String updatedBy) {
        return usuarioAppPort.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Usuario app no encontrado: " + id)))
                .flatMap(u -> {
                    u.setActivo(false);
                    u.setModificaUsuario(updatedBy);
                    return usuarioAppPort.save(u);
                })
                .then();
    }

    @Override
    @Transactional
    public Mono<Void> activar(Integer id, String updatedBy) {
        return usuarioAppPort.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Usuario app no encontrado: " + id)))
                .flatMap(u -> {
                    u.setActivo(true);
                    u.setModificaUsuario(updatedBy);
                    return usuarioAppPort.save(u);
                })
                .then();
    }

    @Override
    public Mono<AppUsuarioResponse> obtenerPorCi(String ci, Integer idCompania) {
        return usuarioAppPort.findByPersonaCiAndIdCompania(ci, idCompania)
                .map(u -> new AppUsuarioResponse(u.getId(), u.getLogin(), u.getActivo(), u.getUltimoAcceso()));
    }

    @Override
    @Transactional
    public Mono<Void> actualizar(Integer id, UpdateAppUsuarioRequest req, String updatedBy) {
        return usuarioAppPort.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Usuario app no encontrado: " + id)))
                .flatMap(u -> {
                    boolean loginChanged = req.login() != null && !req.login().isBlank()
                            && !req.login().equals(u.getLogin());
                    Mono<Void> loginCheck = loginChanged
                            ? usuarioAppPort.findByLoginAndIdCompania(req.login(), u.getIdCompania())
                                    .flatMap(ex -> Mono.<Void>error(new ConflictException("El login ya está en uso")))
                                    .then()
                            : Mono.empty();
                    return loginCheck.then(Mono.defer(() -> {
                        if (loginChanged) u.setLogin(req.login());
                        if (req.password() != null && !req.password().isBlank())
                            u.setPasswordHash(encoder.encode(req.password()));
                        u.setModificaUsuario(updatedBy);
                        return usuarioAppPort.save(u).then();
                    }));
                });
    }

    @Override
    public Flux<AppUsuarioResponse> listarPorPersona(Integer idPersona) {
        return usuarioAppPort.findByIdPersona(idPersona)
                .map(u -> new AppUsuarioResponse(u.getId(), u.getLogin(), u.getActivo(), u.getUltimoAcceso()));
    }
}
