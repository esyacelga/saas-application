package com.gymadmin.platform.unit;

import com.gymadmin.platform.application.service.AprobarPagoService;
import com.gymadmin.platform.domain.exception.PagoYaProcesadoException;
import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.model.PagoPendienteValidacion;
import com.gymadmin.platform.domain.model.Plan;
import com.gymadmin.platform.domain.port.in.ActividadPlataformaUseCase;
import com.gymadmin.platform.domain.port.in.ModuloCheckUseCase;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.domain.port.out.PagoPendienteValidacionRepository;
import com.gymadmin.platform.domain.port.out.PlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AprobarPagoService — RN-08 aprobación atómica")
class AprobarPagoServiceTest {

    @Mock PagoPendienteValidacionRepository pagoRepository;
    @Mock CompaniaPlanRepository companiaPlanRepository;
    @Mock PlanRepository planRepository;
    @Mock ActividadPlataformaUseCase actividadPlataformaUseCase;
    @Mock ModuloCheckUseCase moduloCheckUseCase;

    private AprobarPagoService service;
    private final Clock clockFijo = Clock.fixed(
            LocalDate.of(2026, 7, 9).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new AprobarPagoService(
                pagoRepository, companiaPlanRepository, planRepository,
                actividadPlataformaUseCase, moduloCheckUseCase, clockFijo);
    }

    @Test
    @DisplayName("UPDATE atómico afecta 0 filas → PagoYaProcesadoException")
    void doubleAprobacionConcurrenteLanza() {
        when(pagoRepository.marcarAprobado(eq(1L), eq(42L), any(Instant.class)))
                .thenReturn(Mono.just(0L));

        StepVerifier.create(service.aprobar(1L, 42L))
                .expectError(PagoYaProcesadoException.class)
                .verify();
    }

    @Test
    @DisplayName("aprobación exitosa (no programada) → crea Premium ACTIVO + evento")
    void aprobacionInmediataOk() {
        PagoPendienteValidacion pago = new PagoPendienteValidacion();
        pago.setId(1L);
        pago.setIdCompania(5L);
        pago.setIdPlanDestino(300L);
        pago.setMonto(new BigDecimal("29.99"));
        pago.setActivacionProgramada(false);
        pago.setEstado(PagoPendienteValidacion.Estado.APROBADO);

        Plan premium = new Plan();
        premium.setId(300L);
        premium.setCodigo("PREMIUM");
        premium.setDuracionDias(30);

        when(pagoRepository.marcarAprobado(eq(1L), eq(42L), any(Instant.class)))
                .thenReturn(Mono.just(1L));
        when(pagoRepository.findById(eq(1L))).thenReturn(Mono.just(pago));
        when(planRepository.findById(eq(300L))).thenReturn(Mono.just(premium));
        when(companiaPlanRepository.save(any(CompaniaPlan.class))).thenAnswer(inv -> {
            CompaniaPlan cp = inv.getArgument(0);
            cp.setId(500L);
            return Mono.just(cp);
        });
        when(actividadPlataformaUseCase.registrar(any(ActividadPlataformaUseCase.RegistrarActividadCommand.class)))
                .thenReturn(Mono.empty());
        when(moduloCheckUseCase.invalidateCacheByCompania(eq(5L))).thenReturn(Mono.just(0L));

        StepVerifier.create(service.aprobar(1L, 42L))
                .assertNext(cp -> {
                    org.assertj.core.api.Assertions.assertThat(cp.getEstado())
                            .isEqualTo(CompaniaPlan.Estado.ACTIVO);
                })
                .verifyComplete();
    }
}
