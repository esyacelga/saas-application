package com.gymadmin.platform.unit;

import com.gymadmin.platform.application.service.ConsultarUsoLimitesService;
import com.gymadmin.platform.application.service.LimiteRecursoService;
import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.model.Plan;
import com.gymadmin.platform.domain.model.RecursoLimitable;
import com.gymadmin.platform.domain.port.in.ConsultarUsoLimitesUseCase.UsoLimitesResult;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.domain.port.out.PlanRepository;
import com.gymadmin.platform.infrastructure.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * REQ-SAAS-001 (RN-06, HU-04): unit tests para {@link ConsultarUsoLimitesService}.
 * <p>
 * Cubre en particular el cálculo de {@code diasRestantes}: activo solo para plan TRIAL,
 * puede ser 0/positivo/negativo, y {@code null} para otros planes.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ConsultarUsoLimitesService — RN-06")
class ConsultarUsoLimitesServiceTest {

    @Mock CompaniaPlanRepository companiaPlanRepository;
    @Mock PlanRepository planRepository;
    @Mock LimiteRecursoService limiteRecursoService;

    private static final LocalDate HOY = LocalDate.of(2026, 7, 9);
    private final Clock clockFijo = Clock.fixed(
            HOY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    private ConsultarUsoLimitesService service;

    @BeforeEach
    void setUp() {
        service = new ConsultarUsoLimitesService(
                companiaPlanRepository, planRepository, limiteRecursoService, clockFijo);
    }

    private CompaniaPlan compania(Long idCompania, Long idPlan, LocalDate fechaFin) {
        CompaniaPlan cp = new CompaniaPlan();
        cp.setId(10L);
        cp.setIdCompania(idCompania);
        cp.setIdPlan(idPlan);
        cp.setEstado(CompaniaPlan.Estado.ACTIVO);
        cp.setFechaFin(fechaFin);
        return cp;
    }

    private Plan plan(Long id, String codigo) {
        Plan p = new Plan();
        p.setId(id);
        p.setCodigo(codigo);
        return p;
    }

    private void stubConteos(Long idCompania) {
        when(limiteRecursoService.contarUsoActual(eq(idCompania), eq(RecursoLimitable.SUCURSALES)))
                .thenReturn(Mono.just(0L));
        when(limiteRecursoService.contarUsoActual(eq(idCompania), eq(RecursoLimitable.CLIENTES_ACTIVOS)))
                .thenReturn(Mono.just(0L));
        when(limiteRecursoService.contarUsoActual(eq(idCompania), eq(RecursoLimitable.STAFF)))
                .thenReturn(Mono.just(0L));
    }

    @Test
    @DisplayName("sin suscripción activa → NotFoundException")
    void sinSuscripcionActivaLanza() {
        when(companiaPlanRepository.findActivoByIdCompania(eq(1L))).thenReturn(Mono.empty());

        StepVerifier.create(service.consultar(1L))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("TRIAL vigente → diasRestantes = delta hoy → fechaFin (positivo)")
    void trialVigenteDiasRestantesPositivo() {
        Long idCompania = 1L;
        LocalDate fechaFin = HOY.plusDays(12);
        CompaniaPlan cp = compania(idCompania, 100L, fechaFin);
        Plan trial = plan(100L, "TRIAL");

        when(companiaPlanRepository.findActivoByIdCompania(eq(idCompania))).thenReturn(Mono.just(cp));
        when(planRepository.findById(eq(100L))).thenReturn(Mono.just(trial));
        stubConteos(idCompania);

        StepVerifier.create(service.consultar(idCompania))
                .assertNext(r -> {
                    org.assertj.core.api.Assertions.assertThat(r.planCodigo()).isEqualTo("TRIAL");
                    org.assertj.core.api.Assertions.assertThat(r.diasRestantes()).isEqualTo(12);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("TRIAL vence hoy → diasRestantes = 0")
    void trialVenceHoyEsCero() {
        Long idCompania = 2L;
        CompaniaPlan cp = compania(idCompania, 100L, HOY);
        Plan trial = plan(100L, "TRIAL");

        when(companiaPlanRepository.findActivoByIdCompania(eq(idCompania))).thenReturn(Mono.just(cp));
        when(planRepository.findById(eq(100L))).thenReturn(Mono.just(trial));
        stubConteos(idCompania);

        StepVerifier.create(service.consultar(idCompania))
                .assertNext(r -> org.assertj.core.api.Assertions.assertThat(r.diasRestantes()).isZero())
                .verifyComplete();
    }

    @Test
    @DisplayName("TRIAL vencido → diasRestantes negativo (no lanza)")
    void trialVencidoDevuelveNegativo() {
        Long idCompania = 3L;
        LocalDate fechaFin = HOY.minusDays(5);
        CompaniaPlan cp = compania(idCompania, 100L, fechaFin);
        Plan trial = plan(100L, "TRIAL");

        when(companiaPlanRepository.findActivoByIdCompania(eq(idCompania))).thenReturn(Mono.just(cp));
        when(planRepository.findById(eq(100L))).thenReturn(Mono.just(trial));
        stubConteos(idCompania);

        StepVerifier.create(service.consultar(idCompania))
                .assertNext(r -> org.assertj.core.api.Assertions.assertThat(r.diasRestantes()).isEqualTo(-5))
                .verifyComplete();
    }

    @Test
    @DisplayName("FREE → diasRestantes = null")
    void freeSinDiasRestantes() {
        Long idCompania = 4L;
        CompaniaPlan cp = compania(idCompania, 200L, null);
        Plan free = plan(200L, "FREE");

        when(companiaPlanRepository.findActivoByIdCompania(eq(idCompania))).thenReturn(Mono.just(cp));
        when(planRepository.findById(eq(200L))).thenReturn(Mono.just(free));
        stubConteos(idCompania);

        StepVerifier.create(service.consultar(idCompania))
                .assertNext(r -> {
                    org.assertj.core.api.Assertions.assertThat(r.planCodigo()).isEqualTo("FREE");
                    org.assertj.core.api.Assertions.assertThat(r.diasRestantes()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("PREMIUM con fechaFin → diasRestantes = null (irrelevante para no-TRIAL)")
    void premiumSinDiasRestantes() {
        Long idCompania = 5L;
        CompaniaPlan cp = compania(idCompania, 300L, HOY.plusDays(20));
        Plan premium = plan(300L, "PREMIUM");

        when(companiaPlanRepository.findActivoByIdCompania(eq(idCompania))).thenReturn(Mono.just(cp));
        when(planRepository.findById(eq(300L))).thenReturn(Mono.just(premium));
        stubConteos(idCompania);

        StepVerifier.create(service.consultar(idCompania))
                .assertNext(r -> {
                    org.assertj.core.api.Assertions.assertThat(r.planCodigo()).isEqualTo("PREMIUM");
                    org.assertj.core.api.Assertions.assertThat(r.diasRestantes()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("TRIAL con fechaFin null (defensivo) → diasRestantes = null")
    void trialSinFechaFinDevuelveNull() {
        Long idCompania = 6L;
        CompaniaPlan cp = compania(idCompania, 100L, null);
        Plan trial = plan(100L, "TRIAL");

        when(companiaPlanRepository.findActivoByIdCompania(eq(idCompania))).thenReturn(Mono.just(cp));
        when(planRepository.findById(eq(100L))).thenReturn(Mono.just(trial));
        stubConteos(idCompania);

        StepVerifier.create(service.consultar(idCompania))
                .assertNext(r -> {
                    org.assertj.core.api.Assertions.assertThat(r.planCodigo()).isEqualTo("TRIAL");
                    org.assertj.core.api.Assertions.assertThat(r.diasRestantes()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("smoke: mapea todos los recursos del plan correctamente")
    void mapeoBasicoOk() {
        Long idCompania = 7L;
        CompaniaPlan cp = compania(idCompania, 200L, null);
        Plan free = plan(200L, "FREE");
        free.setMaxSucursales(1);
        free.setMaxClientesActivos(50);
        free.setMaxStaff(2);
        cp.setSobreLimite(true);
        cp.setSobreLimiteHasta(HOY.plusDays(7));

        when(companiaPlanRepository.findActivoByIdCompania(eq(idCompania))).thenReturn(Mono.just(cp));
        when(planRepository.findById(eq(200L))).thenReturn(Mono.just(free));
        when(limiteRecursoService.contarUsoActual(eq(idCompania), eq(RecursoLimitable.SUCURSALES)))
                .thenReturn(Mono.just(1L));
        when(limiteRecursoService.contarUsoActual(eq(idCompania), eq(RecursoLimitable.CLIENTES_ACTIVOS)))
                .thenReturn(Mono.just(48L));
        when(limiteRecursoService.contarUsoActual(eq(idCompania), eq(RecursoLimitable.STAFF)))
                .thenReturn(Mono.just(2L));

        StepVerifier.create(service.consultar(idCompania))
                .assertNext((UsoLimitesResult r) -> {
                    org.assertj.core.api.Assertions.assertThat(r.planCodigo()).isEqualTo("FREE");
                    org.assertj.core.api.Assertions.assertThat(r.sucursales().actual()).isEqualTo(1L);
                    org.assertj.core.api.Assertions.assertThat(r.sucursales().maximo()).isEqualTo(1L);
                    org.assertj.core.api.Assertions.assertThat(r.clientesActivos().actual()).isEqualTo(48L);
                    org.assertj.core.api.Assertions.assertThat(r.clientesActivos().maximo()).isEqualTo(50L);
                    org.assertj.core.api.Assertions.assertThat(r.staff().actual()).isEqualTo(2L);
                    org.assertj.core.api.Assertions.assertThat(r.staff().maximo()).isEqualTo(2L);
                    org.assertj.core.api.Assertions.assertThat(r.sobreLimite()).isTrue();
                    org.assertj.core.api.Assertions.assertThat(r.sobreLimiteHasta()).isEqualTo(HOY.plusDays(7));
                    org.assertj.core.api.Assertions.assertThat(r.diasRestantes()).isNull();
                })
                .verifyComplete();
    }
}
