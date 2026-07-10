package com.gymadmin.platform.infrastructure.adapter.in.web.dto;

import com.gymadmin.platform.domain.port.in.ConsultarUsoLimitesUseCase.UsoLimitesResult;
import com.gymadmin.platform.domain.port.in.ConsultarUsoLimitesUseCase.UsoRecurso;

import java.time.LocalDate;

public record UsoLimitesResponse(
        String planCodigo,
        RecursoUso sucursales,
        RecursoUso clientesActivos,
        RecursoUso staff,
        boolean sobreLimite,
        LocalDate sobreLimiteHasta
) {
    public static UsoLimitesResponse from(UsoLimitesResult r) {
        return new UsoLimitesResponse(
                r.planCodigo(),
                RecursoUso.from(r.sucursales()),
                RecursoUso.from(r.clientesActivos()),
                RecursoUso.from(r.staff()),
                r.sobreLimite(),
                r.sobreLimiteHasta()
        );
    }

    public record RecursoUso(long actual, Long maximo) {
        public static RecursoUso from(UsoRecurso u) {
            return new RecursoUso(u.actual(), u.maximo());
        }
    }
}
