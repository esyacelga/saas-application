package com.gymadmin.platform.application.service;

import com.gymadmin.platform.domain.model.ActividadPlataforma;
import com.gymadmin.platform.domain.port.in.ActividadPlataformaUseCase;
import com.gymadmin.platform.domain.port.out.ActividadPlataformaRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

@Service
public class ActividadPlataformaService implements ActividadPlataformaUseCase {

    private final ActividadPlataformaRepository repository;

    public ActividadPlataformaService(ActividadPlataformaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Flux<ActividadPlataforma> listar(ListarQuery query) {
        return repository.findAll(query);
    }

    @Override
    public Mono<Long> contar(ListarQuery query) {
        return repository.count(query);
    }

    @Override
    public Mono<Void> registrar(RegistrarCommand command) {
        ActividadPlataforma actividad = new ActividadPlataforma();
        actividad.setTipoEvento(command.tipoEvento());
        actividad.setModulo(command.modulo());
        actividad.setEntidadId(command.entidadId());
        actividad.setEntidadNombre(command.entidadNombre());
        actividad.setDetalle(command.detalle());
        actividad.setUsuario(command.usuario());
        actividad.setFecha(OffsetDateTime.now());
        return repository.save(actividad);
    }
}
