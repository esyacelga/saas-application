package com.gymadmin.finance.domain.port.in;

import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ReporteUseCase {

    Mono<ResumenResult> resumen(Integer idCompania, LocalDate desde, LocalDate hasta);

    Mono<MensualResult> mensual(Integer idCompania, Integer anio);

    Mono<ProyeccionResult> proyeccion(Integer idCompania, int mesesBase);

    record CategoriaDetalle(String categoria, BigDecimal monto, Double porcentaje) {}

    record ResumenResult(
            PeriodoInfo periodo,
            BigDecimal totalIngresos,
            BigDecimal totalEgresos,
            BigDecimal utilidad,
            Double margen,
            List<CategoriaDetalle> ingresosPorCategoria,
            List<CategoriaDetalle> egresosPorCategoria
    ) {}

    record PeriodoInfo(LocalDate desde, LocalDate hasta) {}

    record MesDetalle(String mes, BigDecimal ingresos, BigDecimal egresos, BigDecimal utilidad) {}

    record MensualResult(Integer anio, List<MesDetalle> meses) {}

    record ProyeccionResult(
            String mesProyectado,
            BigDecimal ingresosEstimados,
            String baseCalculo
    ) {}
}
