package com.gymadmin.core.domain.port.in;

import com.gymadmin.core.domain.model.Congelamiento;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface CongelamientoUseCase {

    Mono<Congelamiento> congelar(Long idMembresia, Long idCompania, Long idSucursal, Long idUsuario, CongelarCommand command);

    Mono<ReactivarResult> reactivar(Long idCongelamiento, Long idCompania);
    Mono<ReactivarResult> reactivarPorCliente(Long idCongelamiento, Long idPersona, Long idCompania);

    Flux<Congelamiento> historialPorMembresia(Long idMembresia, Long idCompania);

    record CongelarCommand(
        LocalDate fechaInicio,
        Congelamiento.Motivo motivo,
        String detalle,
        boolean retroactivo,
        String documentoRespaldo,
        Long aprobadoPor
    ) {}

    record ReactivarResult(
        LocalDate fechaFinAnterior,
        int diasCompensados,
        LocalDate fechaFinNueva
    ) {}
}
