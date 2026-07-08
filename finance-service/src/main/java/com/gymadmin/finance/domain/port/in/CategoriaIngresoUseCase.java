package com.gymadmin.finance.domain.port.in;

import com.gymadmin.finance.domain.model.CategoriaIngreso;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CategoriaIngresoUseCase {

    Flux<CategoriaIngreso> listar(Integer idCompania, Integer idSucursal);

    Mono<CategoriaIngreso> crear(CrearCommand command);

    Mono<CategoriaIngreso> desactivar(Integer id, Integer idCompania);

    record CrearCommand(
            Integer idCompania,
            Integer idSucursal,
            String nombre
    ) {}
}
