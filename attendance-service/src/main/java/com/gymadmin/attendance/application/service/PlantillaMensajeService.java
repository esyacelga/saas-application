package com.gymadmin.attendance.application.service;

import com.gymadmin.attendance.domain.model.PlantillaMensaje;
import com.gymadmin.attendance.domain.port.in.PlantillaMensajeUseCase;
import com.gymadmin.attendance.domain.port.out.PlantillaMensajeRepository;
import com.gymadmin.attendance.infrastructure.exception.ConflictException;
import com.gymadmin.attendance.infrastructure.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class PlantillaMensajeService implements PlantillaMensajeUseCase {

    private final PlantillaMensajeRepository plantillaRepository;

    @Override
    public Flux<PlantillaMensaje> listar(Integer idCompania) {
        return plantillaRepository.findByCompania(idCompania);
    }

    @Override
    public Mono<PlantillaMensaje> crear(CrearPlantillaCommand command) {
        PlantillaMensaje p = new PlantillaMensaje();
        p.setIdCompania(command.idCompania());
        p.setIdSucursal(command.idSucursal());
        p.setTipo(command.tipo());
        p.setNombre(command.nombre());
        p.setContenido(command.contenido());
        p.setActivo(true);
        return plantillaRepository.save(p);
    }

    @Override
    public Mono<PlantillaMensaje> actualizar(Integer id, ActualizarPlantillaCommand command, Integer idCompania) {
        return plantillaRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Plantilla no encontrada")))
                .flatMap(existing -> {
                    if (!existing.getIdCompania().equals(idCompania)) {
                        return Mono.error(new com.gymadmin.attendance.infrastructure.exception.ForbiddenException(
                                "No tiene acceso a esta plantilla"));
                    }
                    if (command.contenido() != null) existing.setContenido(command.contenido());
                    if (command.activo() != null) existing.setActivo(command.activo());
                    if (command.nombre() != null) existing.setNombre(command.nombre());
                    return plantillaRepository.update(existing);
                });
    }

    @Override
    public Mono<Void> eliminar(Integer id, Integer idCompania) {
        return plantillaRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Plantilla no encontrada")))
                .flatMap(existing -> {
                    if (!existing.getIdCompania().equals(idCompania)) {
                        return Mono.error(new com.gymadmin.attendance.infrastructure.exception.ForbiddenException(
                                "No tiene acceso a esta plantilla"));
                    }
                    // Verificar que no sea la única activa del tipo
                    return plantillaRepository.countActivasByTipo(idCompania, existing.getTipo())
                            .flatMap(count -> {
                                if (count <= 1 && Boolean.TRUE.equals(existing.getActivo())) {
                                    return Mono.error(new ConflictException("ultima_plantilla",
                                            "No se puede eliminar la única plantilla activa del tipo " + existing.getTipo()));
                                }
                                return plantillaRepository.softDelete(id);
                            });
                });
    }
}
