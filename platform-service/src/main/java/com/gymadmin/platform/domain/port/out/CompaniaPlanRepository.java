package com.gymadmin.platform.domain.port.out;

import com.gymadmin.platform.domain.model.CompaniaPlan;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface CompaniaPlanRepository {

    Mono<CompaniaPlan> findById(Long id);

    Mono<CompaniaPlan> findActivoByIdCompania(Long idCompania);

    Flux<CompaniaPlan> findHistorialByIdCompania(Long idCompania);

    Mono<CompaniaPlan> save(CompaniaPlan companiaPlan);

    Flux<CompaniaPlan> findByEstado(String estado);

    Flux<CompaniaPlan> findActivosVencidos(LocalDate today);

    Flux<CompaniaPlan> findEnGraciaVencidos(LocalDate today);

    Flux<CompaniaPlan> findProgramadosParaActivar(LocalDate today);

    Flux<CompaniaPlan> findActivosAndEnGracia();

    Mono<Void> updateEstadoById(Long id, String estado, String motivo);
}
