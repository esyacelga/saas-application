package com.gymadmin.platform.unit;

import com.gymadmin.platform.domain.model.Compania;
import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.model.ConfigNotifSuscripcion;
import com.gymadmin.platform.domain.model.NotifBucketGlobal;
import com.gymadmin.platform.domain.model.NotifBucketGlobal.Destinatario;
import com.gymadmin.platform.domain.model.Plan;
import com.gymadmin.platform.domain.port.in.EnviarNotificacionUseCase;
import com.gymadmin.platform.domain.port.in.EnviarNotificacionUseCase.EncolarNotificacionCommand;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.domain.port.out.CompaniaRepository;
import com.gymadmin.platform.domain.port.out.ConfigNotifRepository;
import com.gymadmin.platform.domain.port.out.NotifBucketGlobalRepository;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REQ-SAAS-001 (Fase 3): con buckets del dueño {@code {3, 0}}, el job encola por canal según
 * {@code config_notif_suscripcion.canal} + {@code banner} siempre, respetando opt-in (R4) y la
 * evaluación por igualdad del día 0 (R2).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificacionVencimientoJob — buckets {3,0}, canal por config + opt-in")
class NotificacionVencimientoJobTest {

    @Mock CompaniaPlanRepository companiaPlanRepository;
    @Mock PlanRepository planRepository;
    @Mock NotificacionRepository notificacionRepository;
    @Mock ConfigNotifRepository configNotifRepository;
    @Mock CompaniaRepository companiaRepository;
    @Mock NotifBucketGlobalRepository notifBucketRepository;
    @Mock EnviarNotificacionUseCase enviarUseCase;

    private final LocalDate hoy = LocalDate.of(2026, 7, 10);
    private final Clock clockFijo = Clock.fixed(hoy.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    private NotificacionVencimientoJob job;

    @BeforeEach
    void setUp() {
        // Fase 6: por defecto el bucket previo del dueño = 3 activo (igual que el seed de la migración),
        // así el ventaneo {3,0} de estos tests se conserva. lenient() por si el flujo corta antes de leerlo.
        lenient().when(notifBucketRepository.findByDestinatario(Destinatario.DUENO))
                .thenReturn(Mono.just(new NotifBucketGlobal(Destinatario.DUENO, 3, true)));
        job = new NotificacionVencimientoJob(companiaPlanRepository, planRepository,
                notificacionRepository, configNotifRepository, companiaRepository,
                notifBucketRepository, enviarUseCase, clockFijo);
    }

    private CompaniaPlan cp(long fechaFinOffsetDays) {
        CompaniaPlan cp = new CompaniaPlan();
        cp.setId(50L);
        cp.setIdCompania(1L);
        cp.setIdPlan(100L);
        cp.setEstado(CompaniaPlan.Estado.ACTIVO);
        cp.setFechaFin(hoy.plusDays(fechaFinOffsetDays));
        return cp;
    }

    private Plan planTrial() {
        Plan p = new Plan();
        p.setId(100L);
        p.setCodigo("TRIAL");
        return p;
    }

    private Compania compania(boolean optIn, String whatsapp) {
        Compania c = new Compania();
        c.setId(1L);
        c.setNombre("PowerGym");
        c.setWhatsapp(whatsapp);
        c.setAceptaWhatsapp(optIn);
        return c;
    }

    private ConfigNotifSuscripcion config(ConfigNotifSuscripcion.Canal canal) {
        return new ConfigNotifSuscripcion(1L, 3, canal, true);
    }

    private Mono<Void> invocarProcesar() throws Exception {
        Method m = NotificacionVencimientoJob.class.getDeclaredMethod("procesar", LocalDate.class);
        m.setAccessible(true);
        @SuppressWarnings("unchecked")
        Mono<Void> resultado = (Mono<Void>) m.invoke(job, hoy);
        return resultado;
    }

    @Test
    @DisplayName("Vence en 3 días + config AMBOS + opt-in + tel válido → encola whatsapp + email + banner (bucket 3)")
    void venceEn3_ambos_optIn() throws Exception {
        when(companiaPlanRepository.findActivosAndEnGracia()).thenReturn(Flux.just(cp(3)));
        when(planRepository.findById(100L)).thenReturn(Mono.just(planTrial()));
        when(companiaRepository.findById(1L)).thenReturn(Mono.just(compania(true, "0987654321")));
        when(configNotifRepository.findByIdCompania(1L))
                .thenReturn(Flux.just(config(ConfigNotifSuscripcion.Canal.AMBOS)));
        when(notificacionRepository.existsIdempotente(eq(50L), eq("VENCIMIENTO_TRIAL"), anyString(), eq(3)))
                .thenReturn(Mono.just(false));
        when(enviarUseCase.encolar(any(EncolarNotificacionCommand.class))).thenReturn(Mono.just(1L));

        StepVerifier.create(invocarProcesar()).verifyComplete();

        ArgumentCaptor<EncolarNotificacionCommand> cap = ArgumentCaptor.forClass(EncolarNotificacionCommand.class);
        verify(enviarUseCase, org.mockito.Mockito.times(3)).encolar(cap.capture());
        List<EncolarNotificacionCommand> cmds = cap.getAllValues();
        assertThat(cmds).extracting(EncolarNotificacionCommand::canal)
                .containsExactlyInAnyOrder("banner", "email", "whatsapp");
        assertThat(cmds).allSatisfy(c -> {
            assertThat(c.tipo()).isEqualTo("VENCIMIENTO_TRIAL");
            assertThat(c.diasAntes()).isEqualTo(3);
            assertThat(c.idCompaniaPlan()).isEqualTo(50L);
        });
    }

    @Test
    @DisplayName("Vence hoy (0 días) + config WHATSAPP → día 0 NO manda whatsapp (decisión 2026-07-15), solo banner")
    void venceHoy_bucket0_sinWhatsapp() throws Exception {
        when(companiaPlanRepository.findActivosAndEnGracia()).thenReturn(Flux.just(cp(0)));
        when(planRepository.findById(100L)).thenReturn(Mono.just(planTrial()));
        when(companiaRepository.findById(1L)).thenReturn(Mono.just(compania(true, "0987654321")));
        when(configNotifRepository.findByIdCompania(1L))
                .thenReturn(Flux.just(config(ConfigNotifSuscripcion.Canal.WHATSAPP)));
        when(notificacionRepository.existsIdempotente(eq(50L), anyString(), anyString(), eq(0)))
                .thenReturn(Mono.just(false));
        when(enviarUseCase.encolar(any(EncolarNotificacionCommand.class))).thenReturn(Mono.just(1L));

        StepVerifier.create(invocarProcesar()).verifyComplete();

        ArgumentCaptor<EncolarNotificacionCommand> cap = ArgumentCaptor.forClass(EncolarNotificacionCommand.class);
        verify(enviarUseCase, org.mockito.Mockito.atLeastOnce()).encolar(cap.capture());
        assertThat(cap.getAllValues()).allSatisfy(c -> assertThat(c.diasAntes()).isEqualTo(0));
        // Día 0: WhatsApp omitido; queda solo el banner (siempre presente).
        assertThat(cap.getAllValues()).extracting(EncolarNotificacionCommand::canal)
                .containsExactly("banner");
    }

    @Test
    @DisplayName("Config WHATSAPP pero opt-in FALSE → NO encola whatsapp (solo banner)")
    void whatsappSinOptIn_omiteWhatsapp() throws Exception {
        when(companiaPlanRepository.findActivosAndEnGracia()).thenReturn(Flux.just(cp(3)));
        when(planRepository.findById(100L)).thenReturn(Mono.just(planTrial()));
        when(companiaRepository.findById(1L)).thenReturn(Mono.just(compania(false, "0987654321")));
        when(configNotifRepository.findByIdCompania(1L))
                .thenReturn(Flux.just(config(ConfigNotifSuscripcion.Canal.WHATSAPP)));
        when(notificacionRepository.existsIdempotente(eq(50L), anyString(), anyString(), eq(3)))
                .thenReturn(Mono.just(false));
        when(enviarUseCase.encolar(any(EncolarNotificacionCommand.class))).thenReturn(Mono.just(1L));

        StepVerifier.create(invocarProcesar()).verifyComplete();

        ArgumentCaptor<EncolarNotificacionCommand> cap = ArgumentCaptor.forClass(EncolarNotificacionCommand.class);
        verify(enviarUseCase, org.mockito.Mockito.times(1)).encolar(cap.capture());
        assertThat(cap.getValue().canal()).isEqualTo("banner");
    }

    @Test
    @DisplayName("Sin config → default EMAIL + banner")
    void sinConfig_defaultEmail() throws Exception {
        when(companiaPlanRepository.findActivosAndEnGracia()).thenReturn(Flux.just(cp(3)));
        when(planRepository.findById(100L)).thenReturn(Mono.just(planTrial()));
        when(companiaRepository.findById(1L)).thenReturn(Mono.just(compania(false, null)));
        when(configNotifRepository.findByIdCompania(1L)).thenReturn(Flux.empty());
        when(notificacionRepository.existsIdempotente(eq(50L), anyString(), anyString(), eq(3)))
                .thenReturn(Mono.just(false));
        when(enviarUseCase.encolar(any(EncolarNotificacionCommand.class))).thenReturn(Mono.just(1L));

        StepVerifier.create(invocarProcesar()).verifyComplete();

        ArgumentCaptor<EncolarNotificacionCommand> cap = ArgumentCaptor.forClass(EncolarNotificacionCommand.class);
        verify(enviarUseCase, org.mockito.Mockito.times(2)).encolar(cap.capture());
        assertThat(cap.getAllValues()).extracting(EncolarNotificacionCommand::canal)
                .containsExactlyInAnyOrder("banner", "email");
    }

    @Test
    @DisplayName("Vence en 10 días → fuera de rango {3,0}, no procesa")
    void venceEn10_fueraDeRango() throws Exception {
        when(companiaPlanRepository.findActivosAndEnGracia()).thenReturn(Flux.just(cp(10)));

        StepVerifier.create(invocarProcesar()).verifyComplete();
        verify(enviarUseCase, org.mockito.Mockito.never()).encolar(any());
    }

    @Test
    @DisplayName("Fase 6: bucket previo dinámico = 7 → un plan que vence en 7 días SÍ se procesa (bucket 7)")
    void bucketPrevioDinamico7_procesaVence7() throws Exception {
        when(notifBucketRepository.findByDestinatario(Destinatario.DUENO))
                .thenReturn(Mono.just(new NotifBucketGlobal(Destinatario.DUENO, 7, true)));
        when(companiaPlanRepository.findActivosAndEnGracia()).thenReturn(Flux.just(cp(7)));
        when(planRepository.findById(100L)).thenReturn(Mono.just(planTrial()));
        when(companiaRepository.findById(1L)).thenReturn(Mono.just(compania(false, null)));
        when(configNotifRepository.findByIdCompania(1L)).thenReturn(Flux.empty());
        when(notificacionRepository.existsIdempotente(eq(50L), anyString(), anyString(), eq(7)))
                .thenReturn(Mono.just(false));
        when(enviarUseCase.encolar(any(EncolarNotificacionCommand.class))).thenReturn(Mono.just(1L));

        StepVerifier.create(invocarProcesar()).verifyComplete();

        ArgumentCaptor<EncolarNotificacionCommand> cap = ArgumentCaptor.forClass(EncolarNotificacionCommand.class);
        verify(enviarUseCase, org.mockito.Mockito.atLeastOnce()).encolar(cap.capture());
        assertThat(cap.getAllValues()).allSatisfy(c -> assertThat(c.diasAntes()).isEqualTo(7));
    }

    @Test
    @DisplayName("Fase 6: bucket previo activo=FALSE → previo omitido; día 0 dispara banner/email (whatsapp aparte)")
    void bucketPrevioDesactivado_soloDia0() throws Exception {
        when(notifBucketRepository.findByDestinatario(Destinatario.DUENO))
                .thenReturn(Mono.just(new NotifBucketGlobal(Destinatario.DUENO, 3, false)));
        // Con previo desactivado (efectivo 0), un plan que vence en 3 días queda fuera de rango.
        when(companiaPlanRepository.findActivosAndEnGracia()).thenReturn(Flux.just(cp(3), cp(0)));
        lenient().when(planRepository.findById(100L)).thenReturn(Mono.just(planTrial()));
        lenient().when(companiaRepository.findById(1L)).thenReturn(Mono.just(compania(false, null)));
        lenient().when(configNotifRepository.findByIdCompania(1L)).thenReturn(Flux.empty());
        lenient().when(notificacionRepository.existsIdempotente(eq(50L), anyString(), anyString(), eq(0)))
                .thenReturn(Mono.just(false));
        lenient().when(enviarUseCase.encolar(any(EncolarNotificacionCommand.class))).thenReturn(Mono.just(1L));

        StepVerifier.create(invocarProcesar()).verifyComplete();

        ArgumentCaptor<EncolarNotificacionCommand> cap = ArgumentCaptor.forClass(EncolarNotificacionCommand.class);
        verify(enviarUseCase, org.mockito.Mockito.atLeastOnce()).encolar(cap.capture());
        // Solo debe haber encolado el bucket 0 (el de 3 días quedó fuera de rango).
        assertThat(cap.getAllValues()).allSatisfy(c -> assertThat(c.diasAntes()).isEqualTo(0));
    }

    @Test
    @DisplayName("Fase 6: sin fila de bucket (tabla vacía) → fallback al default 3")
    void sinFilaBucket_fallbackDefault3() throws Exception {
        when(notifBucketRepository.findByDestinatario(Destinatario.DUENO)).thenReturn(Mono.empty());
        when(companiaPlanRepository.findActivosAndEnGracia()).thenReturn(Flux.just(cp(3)));
        when(planRepository.findById(100L)).thenReturn(Mono.just(planTrial()));
        when(companiaRepository.findById(1L)).thenReturn(Mono.just(compania(false, null)));
        when(configNotifRepository.findByIdCompania(1L)).thenReturn(Flux.empty());
        when(notificacionRepository.existsIdempotente(eq(50L), anyString(), anyString(), eq(3)))
                .thenReturn(Mono.just(false));
        when(enviarUseCase.encolar(any(EncolarNotificacionCommand.class))).thenReturn(Mono.just(1L));

        StepVerifier.create(invocarProcesar()).verifyComplete();

        ArgumentCaptor<EncolarNotificacionCommand> cap = ArgumentCaptor.forClass(EncolarNotificacionCommand.class);
        verify(enviarUseCase, org.mockito.Mockito.atLeastOnce()).encolar(cap.capture());
        assertThat(cap.getAllValues()).allSatisfy(c -> assertThat(c.diasAntes()).isEqualTo(3));
    }

    @Test
    @DisplayName("Notif idempotente ya existente → no encola nada")
    void idempotente_noEncola() throws Exception {
        when(companiaPlanRepository.findActivosAndEnGracia()).thenReturn(Flux.just(cp(3)));
        when(planRepository.findById(100L)).thenReturn(Mono.just(planTrial()));
        when(companiaRepository.findById(1L)).thenReturn(Mono.just(compania(true, "0987654321")));
        when(configNotifRepository.findByIdCompania(1L))
                .thenReturn(Flux.just(config(ConfigNotifSuscripcion.Canal.AMBOS)));
        lenient().when(enviarUseCase.encolar(any())).thenReturn(Mono.just(1L));
        when(notificacionRepository.existsIdempotente(anyLong(), anyString(), anyString(), anyInt()))
                .thenReturn(Mono.just(true));

        StepVerifier.create(invocarProcesar()).verifyComplete();
        verify(enviarUseCase, org.mockito.Mockito.never()).encolar(any());
    }
}
