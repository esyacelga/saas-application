package com.gymadmin.platform.unit;

import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.model.Plan;
import com.gymadmin.platform.domain.port.in.EnviarNotificacionUseCase;
import com.gymadmin.platform.domain.port.in.EnviarNotificacionUseCase.EncolarNotificacionCommand;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.domain.port.out.NotificacionRepository;
import com.gymadmin.platform.domain.port.out.PlanRepository;
import com.gymadmin.platform.infrastructure.scheduler.NotificacionVencimientoJob;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REQ-SAAS-001 (Sub-fase 1.5): a día {@code fechaFin - 15} el job encola 2
 * notificaciones (EMAIL + BANNER) para el bucket T-15.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificacionVencimientoJob — genera 2 notificaciones (email+banner) en T-15")
class NotificacionVencimientoJobTest {

    @Mock CompaniaPlanRepository companiaPlanRepository;
    @Mock PlanRepository planRepository;
    @Mock NotificacionRepository notificacionRepository;
    @Mock EnviarNotificacionUseCase enviarUseCase;

    private final LocalDate hoy = LocalDate.of(2026, 7, 10);
    private final Clock clockFijo = Clock.fixed(hoy.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    private NotificacionVencimientoJob job;

    @BeforeEach
    void setUp() {
        job = new NotificacionVencimientoJob(companiaPlanRepository, planRepository,
                notificacionRepository, enviarUseCase, clockFijo);
    }

    @Test
    @DisplayName("Trial que vence en 15 días → encola EMAIL + BANNER con bucket T-15")
    void encolaAmbosCanalesEnT15() throws Exception {
        CompaniaPlan cp = new CompaniaPlan();
        cp.setId(50L);
        cp.setIdCompania(1L);
        cp.setIdPlan(100L);
        cp.setEstado(CompaniaPlan.Estado.ACTIVO);
        cp.setFechaFin(hoy.plusDays(15));

        Plan planTrial = new Plan();
        planTrial.setId(100L);
        planTrial.setCodigo("TRIAL");

        when(companiaPlanRepository.findActivosAndEnGracia()).thenReturn(Flux.just(cp));
        when(planRepository.findById(100L)).thenReturn(Mono.just(planTrial));
        when(notificacionRepository.existsIdempotente(eq(50L), eq("VENCIMIENTO_TRIAL"), anyString(), eq(15)))
                .thenReturn(Mono.just(false));
        when(enviarUseCase.encolar(any(EncolarNotificacionCommand.class)))
                .thenAnswer(inv -> Mono.just(1L));

        Method m = NotificacionVencimientoJob.class.getDeclaredMethod("procesar", LocalDate.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        Mono<Void> resultado = (Mono<Void>) m.invoke(job, hoy);

        StepVerifier.create(resultado).verifyComplete();

        ArgumentCaptor<EncolarNotificacionCommand> cap = ArgumentCaptor.forClass(EncolarNotificacionCommand.class);
        verify(enviarUseCase, org.mockito.Mockito.times(2)).encolar(cap.capture());

        List<EncolarNotificacionCommand> commands = cap.getAllValues();
        assertThat(commands).extracting(EncolarNotificacionCommand::canal)
                .containsExactlyInAnyOrder("email", "banner");
        assertThat(commands).allSatisfy(c -> {
            assertThat(c.tipo()).isEqualTo("VENCIMIENTO_TRIAL");
            assertThat(c.diasAntes()).isEqualTo(15);
            assertThat(c.idCompania()).isEqualTo(1L);
            assertThat(c.idCompaniaPlan()).isEqualTo(50L);
        });
    }

    @Test
    @DisplayName("Trial en T-15 con notif idempotente ya existente → no encola nada")
    void noEncolaSiExisteIdempotente() throws Exception {
        CompaniaPlan cp = new CompaniaPlan();
        cp.setId(50L);
        cp.setIdCompania(1L);
        cp.setIdPlan(100L);
        cp.setEstado(CompaniaPlan.Estado.ACTIVO);
        cp.setFechaFin(hoy.plusDays(15));

        Plan planTrial = new Plan();
        planTrial.setId(100L);
        planTrial.setCodigo("TRIAL");

        when(companiaPlanRepository.findActivosAndEnGracia()).thenReturn(Flux.just(cp));
        when(planRepository.findById(100L)).thenReturn(Mono.just(planTrial));
        when(notificacionRepository.existsIdempotente(anyLong(), anyString(), anyString(), anyInt()))
                .thenReturn(Mono.just(true));

        Method m = NotificacionVencimientoJob.class.getDeclaredMethod("procesar", LocalDate.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        Mono<Void> resultado = (Mono<Void>) m.invoke(job, hoy);

        StepVerifier.create(resultado).verifyComplete();
        verify(enviarUseCase, org.mockito.Mockito.never()).encolar(any());
    }

    @Test
    @DisplayName("Suscripción a 20 días de vencer → job no la procesa (fuera del rango 15d)")
    void noProcesaSiFechaFinFueraDeRango() throws Exception {
        CompaniaPlan cp = new CompaniaPlan();
        cp.setId(50L);
        cp.setIdCompania(1L);
        cp.setIdPlan(100L);
        cp.setEstado(CompaniaPlan.Estado.ACTIVO);
        cp.setFechaFin(hoy.plusDays(20));

        when(companiaPlanRepository.findActivosAndEnGracia()).thenReturn(Flux.just(cp));

        Method m = NotificacionVencimientoJob.class.getDeclaredMethod("procesar", LocalDate.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        Mono<Void> resultado = (Mono<Void>) m.invoke(job, hoy);

        StepVerifier.create(resultado).verifyComplete();
        verify(enviarUseCase, org.mockito.Mockito.never()).encolar(any());
    }
}
