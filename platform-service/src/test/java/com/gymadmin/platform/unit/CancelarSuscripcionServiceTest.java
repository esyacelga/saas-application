package com.gymadmin.platform.unit;

import com.gymadmin.platform.application.service.CancelarSuscripcionService;
import com.gymadmin.platform.domain.exception.SinSuscripcionCancelableException;
import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.model.Plan;
import com.gymadmin.platform.domain.port.in.ActividadPlataformaUseCase;
import com.gymadmin.platform.domain.port.in.ModuloCheckUseCase;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
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
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CancelarSuscripcionService — RN-09")
class CancelarSuscripcionServiceTest {

    @Mock CompaniaPlanRepository companiaPlanRepository;
    @Mock PlanRepository planRepository;
    @Mock ActividadPlataformaUseCase actividadPlataformaUseCase;
    @Mock ModuloCheckUseCase moduloCheckUseCase;

    private CancelarSuscripcionService service;
    private final Clock clockFijo = Clock.fixed(
            LocalDate.of(2026, 7, 9).atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        service = new CancelarSuscripcionService(
                companiaPlanRepository, planRepository, actividadPlataformaUseCase, moduloCheckUseCase, clockFijo);
    }

    @Test
    @DisplayName("sin suscripción activa → SinSuscripcionCancelableException")
    void sinSuscripcionActivaLanza() {
        when(companiaPlanRepository.findActivoByIdCompania(eq(1L))).thenReturn(Mono.empty());

        StepVerifier.create(service.cancelar(1L, 42L, "ya no lo uso"))
                .expectError(SinSuscripcionCancelableException.class)
                .verify();
    }

    @Test
    @DisplayName("cancela Trial inmediato → transiciona CANCELADO + crea plan Free")
    void cancelaTrialInmediato() {
        CompaniaPlan trial = new CompaniaPlan();
        trial.setId(50L);
        trial.setIdCompania(1L);
        trial.setIdPlan(100L);
        trial.setEstado(CompaniaPlan.Estado.ACTIVO);
        trial.setFechaInicio(LocalDate.of(2026, 5, 9));
        trial.setFechaFin(LocalDate.of(2026, 7, 9).plusDays(30));

        Plan planTrial = new Plan();
        planTrial.setId(100L);
        planTrial.setCodigo("TRIAL");
        Plan planFree = new Plan();
        planFree.setId(200L);
        planFree.setCodigo("FREE");

        when(companiaPlanRepository.findActivoByIdCompania(eq(1L))).thenReturn(Mono.just(trial));
        when(planRepository.findById(eq(100L))).thenReturn(Mono.just(planTrial));
        when(planRepository.findByCodigo(eq("FREE"))).thenReturn(Mono.just(planFree));
        when(companiaPlanRepository.save(any(CompaniaPlan.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(actividadPlataformaUseCase.registrar(any(ActividadPlataformaUseCase.RegistrarActividadCommand.class)))
                .thenReturn(Mono.empty());
        when(moduloCheckUseCase.invalidateCacheByCompania(eq(1L))).thenReturn(Mono.just(0L));

        StepVerifier.create(service.cancelar(1L, 42L, "ya no lo uso mas de 10 chars"))
                .verifyComplete();
    }

    @Test
    @DisplayName("cancela Premium → NO degrada inmediato (solo registra evento)")
    void cancelaPremiumNoDegrada() {
        CompaniaPlan premium = new CompaniaPlan();
        premium.setId(60L);
        premium.setIdCompania(2L);
        premium.setIdPlan(300L);
        premium.setEstado(CompaniaPlan.Estado.ACTIVO);
        premium.setFechaInicio(LocalDate.of(2026, 6, 1));
        premium.setFechaFin(LocalDate.of(2026, 8, 1));

        Plan planPremium = new Plan();
        planPremium.setId(300L);
        planPremium.setCodigo("PREMIUM");

        when(companiaPlanRepository.findActivoByIdCompania(eq(2L))).thenReturn(Mono.just(premium));
        when(planRepository.findById(eq(300L))).thenReturn(Mono.just(planPremium));
        when(actividadPlataformaUseCase.registrar(any(ActividadPlataformaUseCase.RegistrarActividadCommand.class)))
                .thenReturn(Mono.empty());
        when(moduloCheckUseCase.invalidateCacheByCompania(eq(2L))).thenReturn(Mono.just(0L));

        StepVerifier.create(service.cancelar(2L, 42L, "razon suficientemente larga"))
                .verifyComplete();
    }
}
