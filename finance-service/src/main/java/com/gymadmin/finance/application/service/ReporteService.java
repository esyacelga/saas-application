package com.gymadmin.finance.application.service;

import com.gymadmin.finance.domain.port.in.ReporteUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReporteService implements ReporteUseCase {

    private final DatabaseClient db;
    private static final DateTimeFormatter MES_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    @Override
    public Mono<ResumenResult> resumen(Integer idCompania, LocalDate desde, LocalDate hasta) {
        Mono<List<Map<String, Object>>> ingresosPorCatMono = db.sql("""
                        SELECT ci.nombre AS categoria, COALESCE(SUM(i.monto), 0) AS total
                        FROM finanzas.ingresos i
                        JOIN finanzas.categorias_ingreso ci ON ci.id = i.id_categoria
                        WHERE i.id_compania = :idCompania
                          AND i.eliminado = false
                          AND i.fecha BETWEEN :desde AND :hasta
                        GROUP BY ci.nombre
                        ORDER BY total DESC
                        """)
                .bind("idCompania", idCompania)
                .bind("desde", desde)
                .bind("hasta", hasta)
                .fetch().all()
                .collectList();

        Mono<List<Map<String, Object>>> egresosPorCatMono = db.sql("""
                        SELECT ce.nombre AS categoria, COALESCE(SUM(e.monto), 0) AS total
                        FROM finanzas.egresos e
                        JOIN finanzas.categorias_egreso ce ON ce.id = e.id_categoria
                        WHERE e.id_compania = :idCompania
                          AND e.eliminado = false
                          AND e.fecha BETWEEN :desde AND :hasta
                        GROUP BY ce.nombre
                        ORDER BY total DESC
                        """)
                .bind("idCompania", idCompania)
                .bind("desde", desde)
                .bind("hasta", hasta)
                .fetch().all()
                .collectList();

        return Mono.zip(ingresosPorCatMono, egresosPorCatMono)
                .map(tuple -> {
                    List<Map<String, Object>> ingresosCat = tuple.getT1();
                    List<Map<String, Object>> egresosCat = tuple.getT2();

                    BigDecimal totalIngresos = ingresosCat.stream()
                            .map(row -> toBigDecimal(row.get("total")))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal totalEgresos = egresosCat.stream()
                            .map(row -> toBigDecimal(row.get("total")))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal utilidad = totalIngresos.subtract(totalEgresos);

                    double margen = totalIngresos.compareTo(BigDecimal.ZERO) > 0
                            ? utilidad.divide(totalIngresos, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .doubleValue()
                            : 0.0;

                    List<CategoriaDetalle> ingresosDetalle = buildCategoriaDetalle(ingresosCat, totalIngresos);
                    List<CategoriaDetalle> egresosDetalle = buildCategoriaDetalle(egresosCat, totalEgresos);

                    return new ResumenResult(
                            new PeriodoInfo(desde, hasta),
                            totalIngresos,
                            totalEgresos,
                            utilidad,
                            Math.round(margen * 10.0) / 10.0,
                            ingresosDetalle,
                            egresosDetalle
                    );
                });
    }

    @Override
    public Mono<MensualResult> mensual(Integer idCompania, Integer anio) {
        Mono<List<Map<String, Object>>> ingresosMono = db.sql("""
                        SELECT TO_CHAR(fecha, 'YYYY-MM') AS mes, COALESCE(SUM(monto), 0) AS total
                        FROM finanzas.ingresos
                        WHERE id_compania = :idCompania
                          AND eliminado = false
                          AND EXTRACT(YEAR FROM fecha) = :anio
                        GROUP BY mes
                        ORDER BY mes
                        """)
                .bind("idCompania", idCompania)
                .bind("anio", anio)
                .fetch().all()
                .collectList();

        Mono<List<Map<String, Object>>> egresosMono = db.sql("""
                        SELECT TO_CHAR(fecha, 'YYYY-MM') AS mes, COALESCE(SUM(monto), 0) AS total
                        FROM finanzas.egresos
                        WHERE id_compania = :idCompania
                          AND eliminado = false
                          AND EXTRACT(YEAR FROM fecha) = :anio
                        GROUP BY mes
                        ORDER BY mes
                        """)
                .bind("idCompania", idCompania)
                .bind("anio", anio)
                .fetch().all()
                .collectList();

        return Mono.zip(ingresosMono, egresosMono)
                .map(tuple -> {
                    List<Map<String, Object>> ingresosRows = tuple.getT1();
                    List<Map<String, Object>> egresosRows = tuple.getT2();

                    Map<String, BigDecimal> ingMap = toMesMap(ingresosRows);
                    Map<String, BigDecimal> egMap = toMesMap(egresosRows);

                    List<MesDetalle> meses = new ArrayList<>();
                    for (int m = 1; m <= 12; m++) {
                        String mesKey = String.format("%d-%02d", anio, m);
                        BigDecimal ing = ingMap.getOrDefault(mesKey, BigDecimal.ZERO);
                        BigDecimal eg = egMap.getOrDefault(mesKey, BigDecimal.ZERO);
                        meses.add(new MesDetalle(mesKey, ing, eg, ing.subtract(eg)));
                    }

                    return new MensualResult(anio, meses);
                });
    }

    @Override
    public Mono<ProyeccionResult> proyeccion(Integer idCompania, int mesesBase) {
        LocalDate hoy = LocalDate.now();
        LocalDate inicio = hoy.minusMonths(mesesBase).withDayOfMonth(1);
        LocalDate fin = hoy.minusDays(1);

        return db.sql("""
                        SELECT COALESCE(SUM(monto), 0) AS total
                        FROM finanzas.ingresos
                        WHERE id_compania = :idCompania
                          AND eliminado = false
                          AND fecha BETWEEN :inicio AND :fin
                        """)
                .bind("idCompania", idCompania)
                .bind("inicio", inicio)
                .bind("fin", fin)
                .fetch().one()
                .map(row -> {
                    BigDecimal totalBase = toBigDecimal(row.get("total"));
                    BigDecimal promedio = mesesBase > 0
                            ? totalBase.divide(BigDecimal.valueOf(mesesBase), 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;

                    String mesProyectado = YearMonth.from(hoy).format(MES_FORMAT);
                    String baseCalculo = "promedio_" + mesesBase + "_meses";

                    return new ProyeccionResult(mesProyectado, promedio, baseCalculo);
                })
                .switchIfEmpty(Mono.just(new ProyeccionResult(
                        YearMonth.from(hoy).format(MES_FORMAT),
                        BigDecimal.ZERO,
                        "promedio_" + mesesBase + "_meses"
                )));
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(value.toString());
    }

    private Map<String, BigDecimal> toMesMap(List<Map<String, Object>> rows) {
        Map<String, BigDecimal> map = new java.util.HashMap<>();
        for (Map<String, Object> row : rows) {
            String mes = (String) row.get("mes");
            BigDecimal total = toBigDecimal(row.get("total"));
            if (mes != null) map.put(mes, total);
        }
        return map;
    }

    private List<CategoriaDetalle> buildCategoriaDetalle(List<Map<String, Object>> rows, BigDecimal total) {
        List<CategoriaDetalle> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String cat = (String) row.get("categoria");
            BigDecimal monto = toBigDecimal(row.get("total"));
            double pct = total.compareTo(BigDecimal.ZERO) > 0
                    ? monto.divide(total, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue()
                    : 0.0;
            result.add(new CategoriaDetalle(cat, monto, Math.round(pct * 10.0) / 10.0));
        }
        return result;
    }
}
