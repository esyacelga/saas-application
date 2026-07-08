package com.gymadmin.platform.application.service;

import com.gymadmin.platform.domain.model.Caracteristica;
import com.gymadmin.platform.domain.port.in.CaracteristicaUseCase;
import com.gymadmin.platform.domain.port.out.CaracteristicaRepository;
import com.gymadmin.platform.infrastructure.exception.ConflictException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class CaracteristicaService implements CaracteristicaUseCase {

    private final CaracteristicaRepository repository;

    public CaracteristicaService(CaracteristicaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Flux<Caracteristica> listarCaracteristicas() {
        return repository.findAll();
    }

    @Override
    public Mono<Caracteristica> crearCaracteristica(CrearCaracteristicaCommand command) {
        return repository.findByCodigo(command.codigo())
                .flatMap(existing -> Mono.<Caracteristica>error(
                        new ConflictException("Caracteristica with codigo '" + command.codigo() + "' already exists")))
                .switchIfEmpty(Mono.defer(() -> {
                    Caracteristica c = new Caracteristica();
                    c.setCodigo(command.codigo());
                    c.setNombre(command.nombre());
                    c.setModulo(command.modulo());
                    c.setActivo(true);
                    return repository.save(c);
                }));
    }
}
