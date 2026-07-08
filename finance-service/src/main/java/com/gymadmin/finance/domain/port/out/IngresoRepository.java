package com.gymadmin.finance.domain.port.out;

import com.gymadmin.finance.domain.model.Ingreso;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface IngresoRepository {

    Flux<Ingreso> findByFilters(Integer idCompania, LocalDate desde, LocalDate hasta,
                                Integer idCategoria, int limit, long offset);

    Mono<Long> countByFilters(Integer idCompania, LocalDate desde, LocalDate hasta, Integer idCategoria);

    Mono<java.math.BigDecimal> sumByFilters(Integer idCompania, LocalDate desde, LocalDate hasta, Integer idCategoria);

    Mono<Ingreso> save(Ingreso ingreso);
}
