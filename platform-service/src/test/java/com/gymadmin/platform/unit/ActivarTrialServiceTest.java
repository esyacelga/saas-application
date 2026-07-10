package com.gymadmin.platform.unit;

import com.gymadmin.platform.application.service.ActivarTrialService;
import com.gymadmin.platform.domain.exception.SuscripcionActivaException;
import com.gymadmin.platform.domain.exception.TrialYaUsadoException;
import com.gymadmin.platform.domain.model.Compania;
import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.model.Plan;
import com.gymadmin.platform.domain.port.in.ActividadPlataformaUseCase;
import com.gymadmin.platform.domain.port.in.ModuloCheckUseCase;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.domain.port.out.CompaniaRepository;
import com.gymadmin.platform.domain.port.out.PlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ActivarTrialService — RN-01 Trial único e irrevocable")
class ActivarTrialServiceTest {

    @Mock CompaniaRepository companiaRepository;
    @Mock CompaniaPlanRepository companiaPlanRepository;
    @Mock PlanRepository planRepository;
    @Mock ActividadPlataformaUseCase actividadPlataformaUseCase;
    @Mock ModuloCheckUseCase moduloCheckUseCase;

    private ActivarTrialService service;
    private final LocalDate hoy = LocalDate.of(2026, 7, 9);
    private final Clock clockFijo = Clock.fixed(hoy.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new ActivarTrialService(
                companiaRepository, companiaPlanRepository, planRepository,
                actividadPlataformaUseCase, moduloCheckUseCase, clockFijo);
    }

    private Compania buildCompania(Long id, boolean trialUsado) {
        Compania c = new Compania(id, "Gym Test", "1791234567001", null, "099", "099", "gym@test.com", true);
        c.setTrialUsado(trialUsado);
        if (trialUsado) c.setFechaTrialUsado(Instant.parse("2026-01-01T00:00:00Z"));
        return c;
    }

    private Plan buildTrialPlan() {
        Plan p = new Plan();
        p.setId(100L);
        p.setCodigo("TRIAL");
        p.setDuracionDias(60);
        p.setEsGratuito(true);
        return p;
    }

    @Test
    @DisplayName("trial_usado=true → TrialYaUsadoException")
    void trialYaUsadoLanza() {
        when(companiaRepository.findById(eq(1L))).thenReturn(Mono.just(buildCompania(1L, true)));

        StepVerifier.create(service.activar(1L, 42L))
                .expectError(TrialYaUsadoException.class)
                .verify();
    }

    @Test
    @DisplayName("ya tiene suscripción activa → SuscripcionActivaException")
    void suscripcionActivaLanza() {
        when(companiaRepository.findById(eq(1L))).thenReturn(Mono.just(buildCompania(1L, false)));
        CompaniaPlan existente = new CompaniaPlan();
        existente.setId(50L);
        existente.setEstado(CompaniaPlan.Estado.ACTIVO);
        when(companiaPlanRepository.findActivoByIdCompania(eq(1L))).thenReturn(Mono.just(existente));

        StepVerifier.create(service.activar(1L, 42L))
                .expectError(SuscripcionActivaException.class)
                .verify();
    }

    @Test
    @DisplayName("sin trial usado ni suscripción → crea Trial ACTIVO y marca trial_usado")
    void activaTrialCorrectamente() {
        Compania compania = buildCompania(1L, false);
        when(companiaRepository.findById(eq(1L))).thenReturn(Mono.just(compania));
        when(companiaPlanRepository.findActivoByIdCompania(eq(1L))).thenReturn(Mono.empty());
        when(planRepository.findByCodigo(eq("TRIAL"))).thenReturn(Mono.just(buildTrialPlan()));

        CompaniaPlan saved = new CompaniaPlan();
        saved.setId(500L);
        saved.setIdCompania(1L);
        saved.setEstado(CompaniaPlan.Estado.ACTIVO);
        saved.setFechaInicio(hoy);
        saved.setFechaFin(hoy.plusDays(60));
        when(companiaPlanRepository.save(any(CompaniaPlan.class))).thenReturn(Mono.just(saved));
        when(companiaRepository.update(any(Compania.class))).thenReturn(Mono.just(compania));
        when(actividadPlataformaUseCase.registrar(any(ActividadPlataformaUseCase.RegistrarActividadCommand.class)))
                .thenReturn(Mono.empty());
        when(moduloCheckUseCase.invalidateCacheByCompania(eq(1L))).thenReturn(Mono.just(3L));

        StepVerifier.create(service.activar(1L, 42L))
                .assertNext(cp -> {
                    assertThat(cp.getId()).isEqualTo(500L);
                    assertThat(cp.getEstado()).isEqualTo(CompaniaPlan.Estado.ACTIVO);
                    assertThat(cp.getFechaFin()).isEqualTo(hoy.plusDays(60));
                })
                .verifyComplete();

        assertThat(compania.isTrialUsado()).isTrue();
        assertThat(compania.getFechaTrialUsado()).isNotNull();
    }
}
