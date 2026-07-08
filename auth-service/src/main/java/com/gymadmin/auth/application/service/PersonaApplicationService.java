package com.gymadmin.auth.application.service;

import com.gymadmin.auth.domain.exception.ConflictException;
import com.gymadmin.auth.domain.exception.ResourceNotFoundException;
import com.gymadmin.auth.domain.model.Persona;
import com.gymadmin.auth.domain.port.in.PersonaUseCase;
import com.gymadmin.auth.domain.port.out.PersonaPort;
import com.gymadmin.auth.dto.request.CreatePersonaRequest;
import com.gymadmin.auth.dto.request.UpdatePersonaRequest;
import com.gymadmin.auth.dto.response.PersonaPageResponse;
import com.gymadmin.auth.dto.response.PersonaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PersonaApplicationService implements PersonaUseCase {

    private final PersonaPort personaPort;

    @Override
    public Mono<PersonaResponse> findById(Integer id) {
        return personaPort.findById(id)
                .map(this::toResponse)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Persona no encontrada: " + id)));
    }

    @Override
    public Mono<PersonaResponse> findByCi(String ci) {
        return personaPort.findByCi(ci)
                .map(this::toResponse)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Persona no encontrada con CI: " + ci)));
    }

    @Override
    public Mono<PersonaResponse> findByCorreo(String correo) {
        return personaPort.findByCorreo(correo)
                .map(this::toResponse)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Persona no encontrada con correo: " + correo)));
    }

    @Override
    @Transactional
    public Mono<PersonaResponse> create(CreatePersonaRequest req, String createdBy) {
        return personaPort.existsByCi(req.ci())
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists))
                        return Mono.error(new ConflictException("Ya existe una persona con CI: " + req.ci()));
                    Persona p = Persona.builder()
                            .ci(req.ci()).nombre(req.nombre()).telefono(req.telefono())
                            .correo(req.correo()).sexo(req.sexo()).fotoUrl(req.fotoUrl())
                            .fechaNacimiento(req.fechaNacimiento()).creacionUsuario(createdBy)
                            .build();
                    return personaPort.save(p);
                })
                .map(this::toResponse);
    }

    @Override
    @Transactional
    public Mono<PersonaResponse> update(Integer id, UpdatePersonaRequest req, String updatedBy) {
        return personaPort.findById(id)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("Persona no encontrada: " + id)))
                .flatMap(p -> {
                    if (req.nombre() != null) p.setNombre(req.nombre());
                    if (req.telefono() != null) p.setTelefono(req.telefono());
                    if (req.correo() != null) p.setCorreo(req.correo());
                    if (req.fotoUrl() != null) p.setFotoUrl(req.fotoUrl());
                    if (req.sexo() != null) p.setSexo(req.sexo());
                    if (req.fechaNacimiento() != null) p.setFechaNacimiento(req.fechaNacimiento());
                    if (req.ci() != null && !req.ci().isBlank() && !req.ci().equals(p.getCi())) {
                        return personaPort.existsByCiAndIdNot(req.ci(), id)
                                .flatMap(ciTaken -> {
                                    if (Boolean.TRUE.equals(ciTaken))
                                        return Mono.error(new ConflictException("Ya existe otra persona con CI: " + req.ci()));
                                    p.setCi(req.ci());
                                    p.setModificaUsuario(updatedBy);
                                    return personaPort.save(p);
                                });
                    }
                    p.setModificaUsuario(updatedBy);
                    return personaPort.save(p);
                })
                .map(this::toResponse);
    }

    @Override
    public Mono<PersonaPageResponse> listar(String nombre, String ci, String correo, String sexo, int page, int size) {
        int offset = page * size;
        Mono<List<PersonaResponse>> content = personaPort
                .findAll(nombre, ci, correo, sexo, offset, size)
                .map(this::toResponse)
                .collectList();
        Mono<Long> total = personaPort.countAll(nombre, ci, correo, sexo);
        return Mono.zip(content, total)
                .map(t -> new PersonaPageResponse(
                        t.getT1(),
                        t.getT2(),
                        (int) Math.ceil((double) t.getT2() / size),
                        page,
                        size
                ));
    }

    private PersonaResponse toResponse(Persona p) {
        return new PersonaResponse(p.getId(), p.getCi(), p.getNombre(), p.getTelefono(),
                p.getCorreo(), p.getFotoUrl(), p.getSexo(), p.getFechaNacimiento(), p.getCreacionFecha());
    }
}
