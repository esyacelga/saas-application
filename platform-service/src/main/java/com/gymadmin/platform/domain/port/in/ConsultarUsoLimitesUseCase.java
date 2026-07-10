package com.gymadmin.platform.domain.port.in;

import reactor.core.publisher.Mono;

import java.time.LocalDate;

/**
 * REQ-SAAS-001 (RN-06, HU-04): reporte de uso actual vs límites del plan
 * activo de un tenant.
 */
public interface ConsultarUsoLimitesUseCase {

    Mono<UsoLimitesResult> consultar(Long idCompania);

    record UsoRecurso(long actual, Long maximo) {}

    record UsoLimitesResult(
            String planCodigo,
            UsoRecurso sucursales,
            UsoRecurso clientesActivos,
            UsoRecurso staff,
            boolean sobreLimite,
            LocalDate sobreLimiteHasta
    ) {}
}
