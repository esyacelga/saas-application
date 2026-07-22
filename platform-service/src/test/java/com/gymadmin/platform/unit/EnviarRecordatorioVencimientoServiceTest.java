package com.gymadmin.platform.unit;

import com.gymadmin.platform.application.service.EnviarRecordatorioVencimientoService;
import com.gymadmin.platform.application.service.WhatsAppQueueService;
import com.gymadmin.platform.domain.model.Compania;
import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.model.NotificacionSuscripcion;
import com.gymadmin.platform.domain.model.Plan;
import com.gymadmin.platform.domain.port.in.EnviarRecordatorioVencimientoUseCase;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.domain.port.out.CompaniaRepository;
import com.gymadmin.platform.domain.port.out.NotificacionRepository;
import com.gymadmin.platform.domain.port.out.PlanRepository;
import com.gymadmin.platform.domain.port.out.WhatsAppSender;
import com.gymadmin.platform.infrastructure.exception.RecordatorioNoEnviableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GYM-002: disparo manual del recordatorio de vencimiento por WhatsApp. Cubre envío correcto
 * (plantilla por días reales), y los tres fallos de negocio con su {@code codigo} exacto.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EnviarRecordatorioVencimientoService — envío directo + reglas mínimas")
class EnviarRecordatorioVencimientoServiceTest {

    @Mock CompaniaRepository companiaRepository;
    @Mock CompaniaPlanRepository companiaPlanRepository;
    @Mock PlanRepository planRepository;
    @Mock WhatsAppSender whatsAppSender;
    @Mock NotificacionRepository notificacionRepository;

    private EnviarRecordatorioVencimientoService service;

    @BeforeEach
    void setUp() {
        WhatsAppQueueService cola = new WhatsAppQueueService(
                notificacionRepository, companiaRepository, companiaPlanRepository,
                whatsAppSender, Clock.systemUTC());
        service = new EnviarRecordatorioVencimientoService(
                companiaRepository, companiaPlanRepository, planRepository, cola, notificacionRepository);
    }

    /** Por defecto no hay envío previo: la guarda de idempotencia deja pasar. */
    private void sinEnvioPrevio() {
        when(notificacionRepository.fechaEnvioPrevio(eq(50L), anyString(), eq("whatsapp"), any()))
                .thenReturn(Mono.empty());
        when(notificacionRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    private Compania compania(boolean optIn, String whatsapp) {
        Compania c = new Compania();
        c.setId(1L);
        c.setNombre("Carlos");
        c.setWhatsapp(whatsapp);
        c.setAceptaWhatsapp(optIn);
        return c;
    }

    private CompaniaPlan companiaPlan(LocalDate fechaFin) {
        CompaniaPlan cp = new CompaniaPlan();
        cp.setId(50L);
        cp.setIdCompania(1L);
        cp.setIdPlan(9L);
        cp.setFechaFin(fechaFin);
        return cp;
    }

    private Plan plan(String nombre) {
        Plan p = new Plan();
        p.setId(9L);
        p.setNombre(nombre);
        return p;
    }

    @Test
    @DisplayName("opt-in + tel válido + suscripción activa (dias>0) → envía template previo con params en orden")
    void envio_ok() {
        LocalDate fin = LocalDate.now().plusDays(5);
        when(companiaRepository.findById(1L)).thenReturn(Mono.just(compania(true, "0987654321")));
        when(companiaPlanRepository.findActivoByIdCompania(1L)).thenReturn(Mono.just(companiaPlan(fin)));
        when(planRepository.findById(9L)).thenReturn(Mono.just(plan("Premium")));
        when(whatsAppSender.enviarPlantilla(anyString(), anyString(), anyString(), any())).thenReturn(Mono.empty());
        sinEnvioPrevio();

        StepVerifier.create(service.enviar(1L, false))
                .assertNext(r -> {
                    assertThat(r.enviado()).isTrue();
                    assertThat(r.telefono()).isEqualTo("+593987654321");
                    assertThat(r.template()).isEqualTo("recordatorio_vencimiento_suscripcion");
                })
                .verifyComplete();

        ArgumentCaptor<String> tpl = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> params = ArgumentCaptor.forClass(List.class);
        verify(whatsAppSender).enviarPlantilla(eq("+593987654321"), tpl.capture(), eq(WhatsAppQueueService.IDIOMA_DEFAULT), params.capture());
        assertThat(tpl.getValue()).isEqualTo("recordatorio_vencimiento_suscripcion");
        assertThat(params.getValue()).containsExactly(
                "Carlos", "Premium", fin.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")), "5");
    }

    @Test
    @DisplayName("día 0 → envía template 'vence hoy'")
    void envio_dia0_templateHoy() {
        when(companiaRepository.findById(1L)).thenReturn(Mono.just(compania(true, "0987654321")));
        when(companiaPlanRepository.findActivoByIdCompania(1L)).thenReturn(Mono.just(companiaPlan(LocalDate.now())));
        when(planRepository.findById(9L)).thenReturn(Mono.just(plan("Premium")));
        when(whatsAppSender.enviarPlantilla(anyString(), anyString(), anyString(), any())).thenReturn(Mono.empty());
        sinEnvioPrevio();

        StepVerifier.create(service.enviar(1L, false))
                .assertNext(r -> assertThat(r.template()).isEqualTo("venc_suscripcion_hoy"))
                .verifyComplete();

        verify(whatsAppSender).enviarPlantilla(eq("+593987654321"), eq("venc_suscripcion_hoy"), eq(WhatsAppQueueService.IDIOMA_DEFAULT), any());
    }

    @Test
    @DisplayName("acepta_whatsapp=false → RecordatorioNoEnviableException codigo no_consentimiento, no envía")
    void falla_noConsentimiento() {
        when(companiaRepository.findById(1L)).thenReturn(Mono.just(compania(false, "0987654321")));

        StepVerifier.create(service.enviar(1L, false))
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(RecordatorioNoEnviableException.class)
                        .satisfies(ex -> assertThat(((RecordatorioNoEnviableException) ex).getErrorCode().codigo())
                                .isEqualTo("no_consentimiento")))
                .verify();

        verify(whatsAppSender, never()).enviarPlantilla(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("teléfono no normalizable → codigo telefono_invalido, no envía")
    void falla_telefonoInvalido() {
        Compania c = compania(true, null);
        c.setTelefono("022555000"); // fijo, no celular
        when(companiaRepository.findById(1L)).thenReturn(Mono.just(c));

        StepVerifier.create(service.enviar(1L, false))
                .expectErrorSatisfies(e -> assertThat(((RecordatorioNoEnviableException) e).getErrorCode().codigo())
                        .isEqualTo("telefono_invalido"))
                .verify();

        verify(whatsAppSender, never()).enviarPlantilla(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("sin suscripción activa → codigo sin_suscripcion, no envía")
    void falla_sinSuscripcion() {
        when(companiaRepository.findById(1L)).thenReturn(Mono.just(compania(true, "0987654321")));
        when(companiaPlanRepository.findActivoByIdCompania(1L)).thenReturn(Mono.empty());

        StepVerifier.create(service.enviar(1L, false))
                .expectErrorSatisfies(e -> assertThat(((RecordatorioNoEnviableException) e).getErrorCode().codigo())
                        .isEqualTo("sin_suscripcion"))
                .verify();

        verify(whatsAppSender, never()).enviarPlantilla(anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("días vencidos (negativos) → usa template previo, pasa dias negativos a la plantilla")
    void envio_diasVencidos_negativos() {
        LocalDate fin = LocalDate.now().minusDays(4);
        when(companiaRepository.findById(1L)).thenReturn(Mono.just(compania(true, "0987654321")));
        when(companiaPlanRepository.findActivoByIdCompania(1L)).thenReturn(Mono.just(companiaPlan(fin)));
        when(planRepository.findById(9L)).thenReturn(Mono.just(plan("Premium")));
        when(whatsAppSender.enviarPlantilla(anyString(), anyString(), anyString(), any())).thenReturn(Mono.empty());
        sinEnvioPrevio();

        StepVerifier.create(service.enviar(1L, false))
                .assertNext(r -> assertThat(r.template()).isEqualTo("recordatorio_vencimiento_suscripcion"))
                .verifyComplete();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> params = ArgumentCaptor.forClass(List.class);
        verify(whatsAppSender).enviarPlantilla(anyString(), anyString(), anyString(), params.capture());
        assertThat(params.getValue().get(3)).isEqualTo("-4");
    }

    // ── Idempotencia: cada mensaje cuesta, un doble click no debe cobrarse dos veces ──────────

    @Test
    @DisplayName("ya enviado para el bucket → 409 notificacion_ya_enviada con la fecha previa, NO envía")
    void falla_yaEnviado() {
        LocalDateTime previo = LocalDateTime.now().minusHours(3);
        when(companiaRepository.findById(1L)).thenReturn(Mono.just(compania(true, "0987654321")));
        when(companiaPlanRepository.findActivoByIdCompania(1L))
                .thenReturn(Mono.just(companiaPlan(LocalDate.now().plusDays(5))));
        when(planRepository.findById(9L)).thenReturn(Mono.just(plan("Premium")));
        when(notificacionRepository.fechaEnvioPrevio(eq(50L), eq("VENCIMIENTO_PREMIUM"), eq("whatsapp"), eq(5)))
                .thenReturn(Mono.just(previo));

        StepVerifier.create(service.enviar(1L, false))
                .expectErrorSatisfies(e -> {
                    RecordatorioNoEnviableException ex = (RecordatorioNoEnviableException) e;
                    assertThat(ex.getErrorCode().codigo()).isEqualTo("notificacion_ya_enviada");
                    assertThat(ex.getFechaEnvioPrevio()).isEqualTo(previo);
                })
                .verify();

        verify(whatsAppSender, never()).enviarPlantilla(anyString(), anyString(), anyString(), any());
        verify(notificacionRepository, never()).save(any());
    }

    @Test
    @DisplayName("forzar=true → ignora el envío previo, envía y registra otra fila (costo auditable)")
    void forzar_reenvia() {
        when(companiaRepository.findById(1L)).thenReturn(Mono.just(compania(true, "0987654321")));
        when(companiaPlanRepository.findActivoByIdCompania(1L))
                .thenReturn(Mono.just(companiaPlan(LocalDate.now().plusDays(5))));
        when(planRepository.findById(9L)).thenReturn(Mono.just(plan("Premium")));
        when(whatsAppSender.enviarPlantilla(anyString(), anyString(), anyString(), any())).thenReturn(Mono.empty());
        when(notificacionRepository.save(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

        StepVerifier.create(service.enviar(1L, true))
                .assertNext(r -> assertThat(r.enviado()).isTrue())
                .verifyComplete();

        // Con forzar no se consulta la guarda, pero SÍ se registra el envío.
        verify(notificacionRepository, never()).fechaEnvioPrevio(any(), anyString(), anyString(), any());
        verify(notificacionRepository).save(any());
    }

    @Test
    @DisplayName("envío OK → registra fila enviado/whatsapp con el bucket y tipo del plan")
    void registra_envio() {
        when(companiaRepository.findById(1L)).thenReturn(Mono.just(compania(true, "0987654321")));
        when(companiaPlanRepository.findActivoByIdCompania(1L))
                .thenReturn(Mono.just(companiaPlan(LocalDate.now().plusDays(3))));
        Plan trial = plan("Trial");
        trial.setCodigo("TRIAL");
        when(planRepository.findById(9L)).thenReturn(Mono.just(trial));
        when(whatsAppSender.enviarPlantilla(anyString(), anyString(), anyString(), any())).thenReturn(Mono.empty());
        sinEnvioPrevio();

        StepVerifier.create(service.enviar(1L, false)).expectNextCount(1).verifyComplete();

        ArgumentCaptor<NotificacionSuscripcion> captor =
                ArgumentCaptor.forClass(NotificacionSuscripcion.class);
        verify(notificacionRepository).save(captor.capture());
        NotificacionSuscripcion n = captor.getValue();
        assertThat(n.getIdCompaniaPlan()).isEqualTo(50L);
        assertThat(n.getIdCompania()).isEqualTo(1L);
        assertThat(n.getTipo()).isEqualTo("VENCIMIENTO_TRIAL");
        assertThat(n.getCanal()).isEqualTo("whatsapp");
        assertThat(n.getEstado()).isEqualTo("enviado");
        assertThat(n.getDiasAntes()).isEqualTo(3);
        assertThat(n.getFechaEnvio()).isNotNull();
    }

    @Test
    @DisplayName("sender falla → NO registra la fila (no se cobró, debe poder reintentarse sin forzar)")
    void senderFalla_noRegistra() {
        when(companiaRepository.findById(1L)).thenReturn(Mono.just(compania(true, "0987654321")));
        when(companiaPlanRepository.findActivoByIdCompania(1L))
                .thenReturn(Mono.just(companiaPlan(LocalDate.now().plusDays(5))));
        when(planRepository.findById(9L)).thenReturn(Mono.just(plan("Premium")));
        when(notificacionRepository.fechaEnvioPrevio(eq(50L), anyString(), eq("whatsapp"), any()))
                .thenReturn(Mono.empty());
        when(whatsAppSender.enviarPlantilla(anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.error(new RuntimeException("meta 500")));

        StepVerifier.create(service.enviar(1L, false)).expectError().verify();

        verify(notificacionRepository, never()).save(any());
    }

    @Test
    @DisplayName("día 0 → bucket 0 (los días negativos no producen bucket negativo)")
    void bucket_diasNegativos_seClampeaACero() {
        when(companiaRepository.findById(1L)).thenReturn(Mono.just(compania(true, "0987654321")));
        when(companiaPlanRepository.findActivoByIdCompania(1L))
                .thenReturn(Mono.just(companiaPlan(LocalDate.now().minusDays(4))));
        when(planRepository.findById(9L)).thenReturn(Mono.just(plan("Premium")));
        when(whatsAppSender.enviarPlantilla(anyString(), anyString(), anyString(), any())).thenReturn(Mono.empty());
        sinEnvioPrevio();

        StepVerifier.create(service.enviar(1L, false)).expectNextCount(1).verifyComplete();

        verify(notificacionRepository).fechaEnvioPrevio(eq(50L), anyString(), eq("whatsapp"), eq(0));
        ArgumentCaptor<NotificacionSuscripcion> captor =
                ArgumentCaptor.forClass(NotificacionSuscripcion.class);
        verify(notificacionRepository).save(captor.capture());
        assertThat(captor.getValue().getDiasAntes()).isZero();
    }

    @Test
    @DisplayName("resultado implementa el contrato del use case")
    void contrato() {
        EnviarRecordatorioVencimientoUseCase.Resultado r =
                new EnviarRecordatorioVencimientoUseCase.Resultado(true, "+593987654321", "t");
        assertThat(r.enviado()).isTrue();
    }
}
