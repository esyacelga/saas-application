package com.gymadmin.finance.domain.port.out;

import com.gymadmin.finance.domain.model.CategoriaIngreso;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CategoriaIngresoRepository {

    Flux<CategoriaIngreso> findByIdCompania(Integer idCompania);

    Flux<CategoriaIngreso> findByIdCompaniaAndIdSucursal(Integer idCompania, Integer idSucursal);

    Mono<CategoriaIngreso> findByIdAndIdCompania(Integer id, Integer idCompania);

    Mono<CategoriaIngreso> save(CategoriaIngreso categoria);

    Mono<Boolean> existsIngresosByIdCategoria(Integer idCategoria);
}
