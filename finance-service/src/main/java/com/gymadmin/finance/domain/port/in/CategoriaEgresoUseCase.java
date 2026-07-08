package com.gymadmin.finance.domain.port.in;

import com.gymadmin.finance.domain.model.CategoriaEgreso;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CategoriaEgresoUseCase {

    Flux<CategoriaEgreso> listar(Integer idCompania, Integer idSucursal);

    Mono<CategoriaEgreso> crear(CrearCommand command);

    Mono<CategoriaEgreso> desactivar(Integer id, Integer idCompania);

    record CrearCommand(
            Integer idCompania,
            Integer idSucursal,
            String nombre
    ) {}
}
