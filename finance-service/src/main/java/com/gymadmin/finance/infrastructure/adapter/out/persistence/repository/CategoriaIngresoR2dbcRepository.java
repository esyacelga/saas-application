package com.gymadmin.finance.infrastructure.adapter.out.persistence.repository;

import com.gymadmin.finance.infrastructure.adapter.out.persistence.entity.CategoriaIngresoEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CategoriaIngresoR2dbcRepository extends ReactiveCrudRepository<CategoriaIngresoEntity, Integer> {

    @Query("SELECT * FROM finanzas.categorias_ingreso WHERE id_compania = :idCompania AND eliminado = false ORDER BY nombre")
    Flux<CategoriaIngresoEntity> findByIdCompaniaAndEliminadoFalse(Integer idCompania);

    @Query("SELECT * FROM finanzas.categorias_ingreso WHERE id_compania = :idCompania AND id_sucursal = :idSucursal AND eliminado = false ORDER BY nombre")
    Flux<CategoriaIngresoEntity> findByIdCompaniaAndIdSucursalAndEliminadoFalse(Integer idCompania, Integer idSucursal);

    @Query("SELECT * FROM finanzas.categorias_ingreso WHERE id = :id AND id_compania = :idCompania AND eliminado = false")
    Mono<CategoriaIngresoEntity> findByIdAndIdCompaniaAndEliminadoFalse(Integer id, Integer idCompania);

    @Query("SELECT EXISTS(SELECT 1 FROM finanzas.ingresos WHERE id_categoria = :idCategoria AND eliminado = false)")
    Mono<Boolean> existsByIdCategoria(Integer idCategoria);
}
