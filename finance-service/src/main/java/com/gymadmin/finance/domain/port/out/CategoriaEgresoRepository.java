package com.gymadmin.finance.domain.port.out;

import com.gymadmin.finance.domain.model.CategoriaEgreso;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CategoriaEgresoRepository {

    Flux<CategoriaEgreso> findByIdCompania(Integer idCompania);

    Flux<CategoriaEgreso> findByIdCompaniaAndIdSucursal(Integer idCompania, Integer idSucursal);

    Mono<CategoriaEgreso> findByIdAndIdCompania(Integer id, Integer idCompania);

    Mono<CategoriaEgreso> save(CategoriaEgreso categoria);

    Mono<Boolean> existsEgresosByIdCategoria(Integer idCategoria);
}
