package com.gymadmin.finance.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.finance.infrastructure.adapter.out.persistence.entity.CategoriaEgresoEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CategoriaEgresoR2dbcRepository extends ReactiveCrudRepository<CategoriaEgresoEntity, Integer> {

    @Query("SELECT * FROM finanzas.categorias_egreso WHERE id_compania = :idCompania AND eliminado = false ORDER BY nombre")
    Flux<CategoriaEgresoEntity> findByIdCompaniaAndEliminadoFalse(Integer idCompania);

    @Query("SELECT * FROM finanzas.categorias_egreso WHERE id_compania = :idCompania AND id_sucursal = :idSucursal AND eliminado = false ORDER BY nombre")
    Flux<CategoriaEgresoEntity> findByIdCompaniaAndIdSucursalAndEliminadoFalse(Integer idCompania, Integer idSucursal);

    @Query("SELECT * FROM finanzas.categorias_egreso WHERE id = :id AND id_compania = :idCompania AND eliminado = false")
    Mono<CategoriaEgresoEntity> findByIdAndIdCompaniaAndEliminadoFalse(Integer id, Integer idCompania);

    @Query("SELECT EXISTS(SELECT 1 FROM finanzas.egresos WHERE id_categoria = :idCategoria AND eliminado = false)")
    Mono<Boolean> existsByIdCategoria(Integer idCategoria);
}
