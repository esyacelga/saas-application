package com.gymadmin.billing.domain.port.out;

import com.gymadmin.billing.domain.model.Comprobante;
import com.gymadmin.billing.domain.model.PeriodoResumen;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface ReporteRepository {

    Flux<Comprobante> findAutorizadosPorMes(Integer idCompania, Integer anio, Integer mes);

    Mono<PeriodoResumen> resumenPorPeriodo(Integer idCompania, LocalDate desde, LocalDate hasta);
}
