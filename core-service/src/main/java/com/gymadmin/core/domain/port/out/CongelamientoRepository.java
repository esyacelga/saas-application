package com.gymadmin.core.domain.port.out;

import com.gymadmin.core.domain.model.Congelamiento;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CongelamientoRepository {

    Flux<Congelamiento> findByIdMembresia(Long idMembresia);

    Mono<Congelamiento> findById(Long id);

    Mono<Congelamiento> findActivoByIdMembresia(Long idMembresia);

    Mono<Congelamiento> save(Congelamiento congelamiento);
}
