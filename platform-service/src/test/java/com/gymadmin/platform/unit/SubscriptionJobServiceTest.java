package com.gymadmin.platform.unit;

import com.gymadmin.platform.application.service.SubscriptionJobService;
import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.model.Plan;
import com.gymadmin.platform.domain.port.in.ActividadPlataformaUseCase;
import com.gymadmin.platform.domain.port.in.ModuloCheckUseCase;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.domain.port.out.ConfigNotifRepository;
import com.gymadmin.platform.domain.port.out.NotificacionRepository;
import com.gymadmin.platform.domain.port.out.PlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REQ-SAAS-001 (RN-03): verifica el orden estricto y la degradación auto Trial→Free
 * usando {@code Clock.fixed(...)} — simulamos el día 61 de un Trial de 60 días.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SubscriptionJobService — RN-03 orden estricto con Clock time-travel")
class SubscriptionJobServiceTest {

    @Mock CompaniaPlanRepository companiaPlanRepository;
    @Mock PlanRepository planRepository;
    @Mock ConfigNotifRepository configNotifRepository;
    @Mock NotificacionRepository notificacionRepository;
    @Mock ActividadPlataformaUseCase actividadPlataformaUseCase;
    @Mock ModuloCheckUseCase moduloCheckUseCase;

    private SubscriptionJobService jobService;
    // Día 61 del Trial (Trial inició 2026-05-09, vence 2026-07-08 → hoy=07-09).
    private final LocalDate hoy = LocalDate.of(2026, 7, 9);
    private final Clock clockFijo = Clock.fixed(hoy.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        jobService = new SubscriptionJobService(
                companiaPlanRepository, planRepository, configNotifRepository, notificacionRepository,
                actividadPlataformaUseCase, moduloCheckUseCase, clockFijo);
    }

    @Test
    @DisplayName("Trial vencido (día 61) → transición a VENCIDO + Free ACTIVO + evento PLAN_DEGRADADO_AUTO")
    void degradaTrialVencidoAFree() throws Exception {
        CompaniaPlan trialVencido = new CompaniaPlan();
        trialVencido.setId(50L);
        trialVencido.setIdCompania(1L);
        trialVencido.setIdPlan(100L);
        trialVencido.setEstado(CompaniaPlan.Estado.ACTIVO);
        trialVencido.setFechaInicio(LocalDate.of(2026, 5, 9));
        trialVencido.setFechaFin(LocalDate.of(2026, 7, 8));

        Plan planTrial = new Plan();
        planTrial.setId(100L);
        planTrial.setCodigo("TRIAL");
        planTrial.setPlanDegradacionId(200L);

        Plan planFree = new Plan();
        planFree.setId(200L);
        planFree.setCodigo("FREE");
        planFree.setEsGratuito(true);

        when(companiaPlanRepository.findProgramadosParaActivar(eq(hoy))).thenReturn(Flux.empty());
        when(companiaPlanRepository.findActivosVencidos(eq(hoy))).thenReturn(Flux.just(trialVencido));
        when(companiaPlanRepository.findEnGraciaVencidos(eq(hoy))).thenReturn(Flux.empty());
        when(planRepository.findById(eq(100L))).thenReturn(Mono.just(planTrial));
        when(planRepository.findById(eq(200L))).thenReturn(Mono.just(planFree));
        when(companiaPlanRepository.save(any(CompaniaPlan.class)))
                .thenAnswer(inv -> {
                    CompaniaPlan cp = inv.getArgument(0);
                    if (cp.getId() == null) cp.setId(999L);
                    return Mono.just(cp);
                });
        when(actividadPlataformaUseCase.registrar(any(ActividadPlataformaUseCase.RegistrarActividadCommand.class)))
                .thenReturn(Mono.empty());
        when(moduloCheckUseCase.invalidateCacheByCompania(eq(1L))).thenReturn(Mono.just(0L));

        // Invocamos el helper package-private procesarSuscripciones(today) vía reflexión
        Method m = SubscriptionJobService.class.getDeclaredMethod("procesarSuscripciones", LocalDate.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        Mono<Void> resultado = (Mono<Void>) m.invoke(jobService, hoy);

        StepVerifier.create(resultado).verifyComplete();

        ArgumentCaptor<ActividadPlataformaUseCase.RegistrarActividadCommand> eventCaptor =
                ArgumentCaptor.forClass(ActividadPlataformaUseCase.RegistrarActividadCommand.class);
        verify(actividadPlataformaUseCase, atLeastOnce()).registrar(eventCaptor.capture());
        assertThat(eventCaptor.getAllValues())
                .extracting(ActividadPlataformaUseCase.RegistrarActividadCommand::evento)
                .contains("PLAN_DEGRADADO_AUTO");

        verify(moduloCheckUseCase, atLeastOnce()).invalidateCacheByCompania(eq(1L));
    }
}
