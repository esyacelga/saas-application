package com.gymadmin.finance.unit;

import com.gymadmin.finance.application.service.ReporteService;
import com.gymadmin.finance.domain.port.in.ReporteUseCase.MensualResult;
import com.gymadmin.finance.domain.port.in.ReporteUseCase.ProyeccionResult;
import com.gymadmin.finance.domain.port.in.ReporteUseCase.ResumenResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec;
import org.springframework.r2dbc.core.FetchSpec;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReporteService — reportes financieros")
class ReporteServiceTest {

    @Mock
    private DatabaseClient db;

    @Mock
    private GenericExecuteSpec execSpec;

    @Mock
    private FetchSpec<Map<String, Object>> fetchSpec;

    private ReporteService service;

    @BeforeEach
    void setUp() {
        service = new ReporteService(db);
    }

    private void mockDbQuery(List<Map<String, Object>> ingresos, List<Map<String, Object>> egresos) {
        when(db.sql(anyString())).thenReturn(execSpec);
        when(execSpec.bind(anyString(), anyString())).thenReturn(execSpec);
        when(execSpec.bind(anyString(), (Object) org.mockito.ArgumentMatchers.any())).thenReturn(execSpec);
        when(execSpec.fetch()).thenReturn(fetchSpec);

        when(fetchSpec.all())
                .thenReturn(Flux.fromIterable(ingresos))
                .thenReturn(Flux.fromIterable(egresos));
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("resumen")
    class Resumen {

        @Test
        @DisplayName("calcula correctamente ingresos, egresos y utilidad del período")
        void calculaResumenExitosamente() {
            LocalDate desde = LocalDate.now().withDayOfMonth(1);
            LocalDate hasta = LocalDate.now();

            Map<String, Object> ingRow = Map.of("categoria", "Mensualidad", "total", BigDecimal.valueOf(500.00));
            Map<String, Object> egRow  = Map.of("categoria", "Arriendo",   "total", BigDecimal.valueOf(200.00));

            when(db.sql(anyString())).thenReturn(execSpec);
            when(execSpec.bind(anyString(), (Object) org.mockito.ArgumentMatchers.any())).thenReturn(execSpec);
            when(execSpec.fetch()).thenReturn(fetchSpec);
            when(fetchSpec.all())
                    .thenReturn(Flux.just(ingRow))
                    .thenReturn(Flux.just(egRow));

            StepVerifier.create(service.resumen(1, desde, hasta))
                    .assertNext(r -> {
                        assertThat(r.totalIngresos()).isEqualByComparingTo(BigDecimal.valueOf(500.00));
                        assertThat(r.totalEgresos()).isEqualByComparingTo(BigDecimal.valueOf(200.00));
                        assertThat(r.utilidad()).isEqualByComparingTo(BigDecimal.valueOf(300.00));
                        assertThat(r.margen()).isGreaterThan(0);
                        assertThat(r.ingresosPorCategoria()).hasSize(1);
                        assertThat(r.egresosPorCategoria()).hasSize(1);
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna margen=0 cuando no hay ingresos")
        void retornaMargenCeroCuandoSinIngresos() {
            LocalDate desde = LocalDate.now().withDayOfMonth(1);
            LocalDate hasta = LocalDate.now();

            when(db.sql(anyString())).thenReturn(execSpec);
            when(execSpec.bind(anyString(), (Object) org.mockito.ArgumentMatchers.any())).thenReturn(execSpec);
            when(execSpec.fetch()).thenReturn(fetchSpec);
            when(fetchSpec.all())
                    .thenReturn(Flux.empty())
                    .thenReturn(Flux.empty());

            StepVerifier.create(service.resumen(1, desde, hasta))
                    .assertNext(r -> {
                        assertThat(r.totalIngresos()).isEqualByComparingTo(BigDecimal.ZERO);
                        assertThat(r.margen()).isEqualTo(0.0);
                    })
                    .verifyComplete();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("mensual")
    class Mensual {

        @Test
        @DisplayName("genera 12 meses completos del año aunque solo haya datos en algunos")
        void genera12MesesCompletos() {
            Map<String, Object> ingRow = Map.of("mes", "2026-01", "total", BigDecimal.valueOf(300.00));
            Map<String, Object> egRow  = Map.of("mes", "2026-01", "total", BigDecimal.valueOf(100.00));

            when(db.sql(anyString())).thenReturn(execSpec);
            when(execSpec.bind(anyString(), (Object) org.mockito.ArgumentMatchers.any())).thenReturn(execSpec);
            when(execSpec.fetch()).thenReturn(fetchSpec);
            when(fetchSpec.all())
                    .thenReturn(Flux.just(ingRow))
                    .thenReturn(Flux.just(egRow));

            StepVerifier.create(service.mensual(1, 2026))
                    .assertNext(r -> {
                        assertThat(r.anio()).isEqualTo(2026);
                        assertThat(r.meses()).hasSize(12);

                        var enero = r.meses().stream().filter(m -> m.mes().equals("2026-01")).findFirst().orElseThrow();
                        assertThat(enero.ingresos()).isEqualByComparingTo(BigDecimal.valueOf(300.00));
                        assertThat(enero.egresos()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
                        assertThat(enero.utilidad()).isEqualByComparingTo(BigDecimal.valueOf(200.00));

                        var febrero = r.meses().stream().filter(m -> m.mes().equals("2026-02")).findFirst().orElseThrow();
                        assertThat(febrero.ingresos()).isEqualByComparingTo(BigDecimal.ZERO);
                    })
                    .verifyComplete();
        }
    }

    // -------------------------------------------------------------------------
    @Nested
    @DisplayName("proyeccion")
    class Proyeccion {

        @Test
        @DisplayName("calcula el promedio mensual basado en los últimos N meses")
        void calculaPromedioMensual() {
            Map<String, Object> row = Map.of("total", BigDecimal.valueOf(600.00));

            when(db.sql(anyString())).thenReturn(execSpec);
            when(execSpec.bind(anyString(), (Object) org.mockito.ArgumentMatchers.any())).thenReturn(execSpec);
            when(execSpec.fetch()).thenReturn(fetchSpec);
            when(fetchSpec.one()).thenReturn(Mono.just(row));

            StepVerifier.create(service.proyeccion(1, 3))
                    .assertNext(r -> {
                        assertThat(r.ingresosEstimados()).isEqualByComparingTo(BigDecimal.valueOf(200.00));
                        assertThat(r.baseCalculo()).isEqualTo("promedio_3_meses");
                    })
                    .verifyComplete();
        }

        @Test
        @DisplayName("retorna promedio=ZERO cuando no hay datos en el período base")
        void retornaCeroCuandoSinDatos() {
            when(db.sql(anyString())).thenReturn(execSpec);
            when(execSpec.bind(anyString(), (Object) org.mockito.ArgumentMatchers.any())).thenReturn(execSpec);
            when(execSpec.fetch()).thenReturn(fetchSpec);
            when(fetchSpec.one()).thenReturn(Mono.empty());

            StepVerifier.create(service.proyeccion(1, 3))
                    .assertNext(r -> assertThat(r.ingresosEstimados()).isEqualByComparingTo(BigDecimal.ZERO))
                    .verifyComplete();
        }
    }
}
