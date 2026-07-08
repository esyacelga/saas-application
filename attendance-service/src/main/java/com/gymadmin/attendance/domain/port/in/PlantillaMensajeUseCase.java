package com.gymadmin.attendance.domain.port.in;

import com.gymadmin.attendance.domain.model.PlantillaMensaje;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PlantillaMensajeUseCase {

    Flux<PlantillaMensaje> listar(Integer idCompania);

    Mono<PlantillaMensaje> crear(CrearPlantillaCommand command);

    Mono<PlantillaMensaje> actualizar(Integer id, ActualizarPlantillaCommand command, Integer idCompania);

    Mono<Void> eliminar(Integer id, Integer idCompania);

    record CrearPlantillaCommand(Integer idCompania, Integer idSucursal,
                                  String tipo, String nombre, String contenido) {}

    record ActualizarPlantillaCommand(String contenido, Boolean activo, String nombre) {}
}
