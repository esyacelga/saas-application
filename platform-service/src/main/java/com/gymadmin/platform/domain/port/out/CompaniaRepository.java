package com.gymadmin.platform.domain.port.out;

import com.gymadmin.platform.domain.model.Compania;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CompaniaRepository {

    Flux<Compania> findAll();

    Mono<Compania> findById(Long id);

    Mono<Compania> findByRuc(String ruc);

    Mono<Compania> save(Compania compania);

    Mono<Compania> update(Compania compania);
}
