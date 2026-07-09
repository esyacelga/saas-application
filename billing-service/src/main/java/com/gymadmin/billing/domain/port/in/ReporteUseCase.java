package com.gymadmin.billing.domain.port.in;

import com.gymadmin.billing.domain.model.PeriodoResumen;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface ReporteUseCase {

    Mono<byte[]> generarAts(Integer idCompania, Integer anio, Integer mes);

    Mono<PeriodoResumen> resumenPeriodo(Integer idCompania, LocalDate desde, LocalDate hasta);
}
