package com.gymadmin.core.domain.port.in;

import com.gymadmin.core.domain.model.TipoMembresia;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TipoMembresiaUseCase {

    Flux<TipoMembresia> listarActivos(Long idCompania);

    Mono<TipoMembresia> crear(Long idCompania, Long idSucursal, CrearTipoCommand command);

    Mono<TipoMembresia> actualizar(Long id, Long idCompania, ActualizarTipoCommand command);

    Mono<Void> desactivar(Long id, Long idCompania);

    record CrearTipoCommand(
        String nombre,
        TipoMembresia.ModoControl modoControl,
        TipoMembresia.DuracionTipo duracionTipo,
        Integer duracionValor,
        Integer diasAcceso,
        java.math.BigDecimal precio
    ) {}

    record ActualizarTipoCommand(
        String nombre,
        java.math.BigDecimal precio
    ) {}
}
