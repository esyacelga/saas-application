package com.gymadmin.platform.unit;

import com.gymadmin.platform.application.service.WhatsAppQueueService;
import com.gymadmin.platform.domain.exception.WhatsAppSendException;
import com.gymadmin.platform.domain.model.Compania;
import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.model.NotificacionSuscripcion;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.domain.port.out.CompaniaRepository;
import com.gymadmin.platform.domain.port.out.NotificacionRepository;
import com.gymadmin.platform.domain.port.out.WhatsAppSender;
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

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REQ-SAAS-001 (Fase 3): worker de la cola WhatsApp del dueño. Cubre ruteo de plantilla por
 * {@code dias_antes}, params en orden (R3 fecha_vencimiento), skip por teléfono inválido,
 * clasificación retryable/no-retryable y la regla cross-day del día 0.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WhatsAppQueueService — envío HSM del dueño + backoff + cross-day día 0")
class WhatsAppQueueServiceTest {

    @Mock NotificacionRepository notificacionRepository;
    @Mock CompaniaRepository companiaRepository;
    @Mock CompaniaPlanRepository companiaPlanRepository;
    @Mock WhatsAppSender whatsAppSender;

    private WhatsAppQueueService service;

    private Clock clockAlMediodia() {
        LocalDateTime t = LocalDate.of(2026, 7, 10).atTime(12, 0);
        return Clock.fixed(t.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }

    private Clock clockCasiMedianoche() {
        LocalDateTime t = LocalDate.of(2026, 7, 10).atTime(23, 59, 50);
        return Clock.fixed(t.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }

    @BeforeEach
    void setUp() {
        service = new WhatsAppQueueService(notificacionRepository, companiaRepository,
                companiaPlanRepository, whatsAppSender, clockAlMediodia());
    }

    private NotificacionSuscripcion notif(int diasAntes, int intentos) {
        NotificacionSuscripcion n = new NotificacionSuscripcion();
        n.setId(500L);
        n.setIdCompania(1L);
        n.setIdCompaniaPlan(50L);
        n.setTipo("VENCIMIENTO_PREMIUM");
        n.setDiasAntes(diasAntes);
        n.setCanal(NotificacionSuscripcion.CANAL_WHATSAPP);
        n.setEstado(NotificacionSuscripcion.ESTADO_PENDIENTE);
        n.setIntentos(intentos);
        return n;
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
        cp.setFechaFin(fechaFin);
        return cp;
    }

    @Test
    @DisplayName("dias>0 + opt-in + tel válido + 200 → marcarEnviado, template previo, params en orden con fecha (R3)")
    void previo_ok_paramsEnOrden() {
        when(notificacionRepository.claimLoteWhatsapp(50)).thenReturn(Flux.just(notif(3, 0)));
        when(companiaRepository.findById(1L)).thenReturn(Mono.just(compania(true, "0987654321")));
        when(companiaPlanRepository.findById(50L)).thenReturn(Mono.just(companiaPlan(LocalDate.of(2026, 7, 13))));
        when(whatsAppSender.enviarPlantilla(anyString(), anyString(), anyString(), any())).thenReturn(Mono.empty());
        when(notificacionRepository.marcarEnviado(500L)).thenReturn(Mono.empty());

        StepVerifier.create(service.procesarLote(50)).expectNext(1).verifyComplete();

        ArgumentCaptor<String> tel = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> tpl = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> params = ArgumentCaptor.forClass(List.class);
        verify(whatsAppSender).enviarPlantilla(tel.capture(), tpl.capture(), eq("es"), params.capture());

        assertThat(tel.getValue()).isEqualTo("+593987654321");
        assertThat(tpl.getValue()).isEqualTo("recordatorio_vencimiento_suscripcion");
        assertThat(params.getValue()).containsExactly("Carlos", "Premium", "13/07/2026", "3");
        verify(notificacionRepository).marcarEnviado(500L);
    }

    @Test
    @DisplayName("dias=0 → template hoy, params [nombre, plan]")
    void hoy_ok_dosParams() {
        when(notificacionRepository.claimLoteWhatsapp(50)).thenReturn(Flux.just(notif(0, 0)));
        when(companiaRepository.findById(1L)).thenReturn(Mono.just(compania(true, "0987654321")));
        when(whatsAppSender.enviarPlantilla(anyString(), anyString(), anyString(), any())).thenReturn(Mono.empty());
        when(notificacionRepository.marcarEnviado(500L)).thenReturn(Mono.empty());

        StepVerifier.create(service.procesarLote(50)).expectNext(1).verifyComplete();

        ArgumentCaptor<String> tpl = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> params = ArgumentCaptor.forClass(List.class);
        verify(whatsAppSender).enviarPlantilla(anyString(), tpl.capture(), eq("es"), params.capture());
        assertThat(tpl.getValue()).isEqualTo("venc_suscripcion_hoy");
        assertThat(params.getValue()).containsExactly("Carlos", "Premium");
        // dia 0 no debe consultar el CompaniaPlan (no arma fecha)
        verify(companiaPlanRepository, never()).findById(any());
    }

    @Test
    @DisplayName("teléfono no normalizable → marcarFallido 'telefono_invalido', nunca llama al sender")
    void telefonoInvalido_skip() {
        when(notificacionRepository.claimLoteWhatsapp(50)).thenReturn(Flux.just(notif(3, 0)));
        when(companiaRepository.findById(1L)).thenReturn(Mono.just(compania(true, "123")));
        when(notificacionRepository.marcarFallido(eq(500L), anyString())).thenReturn(Mono.empty());

        StepVerifier.create(service.procesarLote(50)).expectNext(1).verifyComplete();

        verify(whatsAppSender, never()).enviarPlantilla(anyString(), anyString(), anyString(), any());
        verify(notificacionRepository).marcarFallido(eq(500L), eq("telefono_invalido"));
    }

    @Test
    @DisplayName("WhatsAppSendException retryable (dias>0, intentos<MAX) → marcarReintentar con backoff 30s")
    void retryable_marcarReintentar() {
        when(notificacionRepository.claimLoteWhatsapp(50)).thenReturn(Flux.just(notif(3, 0)));
        when(companiaRepository.findById(1L)).thenReturn(Mono.just(compania(true, "0987654321")));
        when(companiaPlanRepository.findById(50L)).thenReturn(Mono.just(companiaPlan(LocalDate.of(2026, 7, 13))));
        when(whatsAppSender.enviarPlantilla(anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.error(new WhatsAppSendException("rate limit", true, 131056)));
        when(notificacionRepository.marcarReintentar(eq(500L), eq(1), anyString(), any())).thenReturn(Mono.empty());

        StepVerifier.create(service.procesarLote(50)).expectNext(1).verifyComplete();

        ArgumentCaptor<OffsetDateTime> proximo = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(notificacionRepository).marcarReintentar(eq(500L), eq(1), anyString(), proximo.capture());
        // clock fijo al mediodía + 30s
        assertThat(proximo.getValue()).isEqualTo(OffsetDateTime.of(2026, 7, 10, 12, 0, 30, 0, ZoneOffset.UTC));
        verify(notificacionRepository, never()).marcarFallido(any(), anyString());
    }

    @Test
    @DisplayName("WhatsAppSendException NO retryable → marcarFallido inmediato (con meta_code)")
    void noRetryable_marcarFallido() {
        when(notificacionRepository.claimLoteWhatsapp(50)).thenReturn(Flux.just(notif(3, 0)));
        when(companiaRepository.findById(1L)).thenReturn(Mono.just(compania(true, "0987654321")));
        when(companiaPlanRepository.findById(50L)).thenReturn(Mono.just(companiaPlan(LocalDate.of(2026, 7, 13))));
        when(whatsAppSender.enviarPlantilla(anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.error(new WhatsAppSendException("sin consentimiento", false, 131047)));
        ArgumentCaptor<String> err = ArgumentCaptor.forClass(String.class);
        when(notificacionRepository.marcarFallido(eq(500L), err.capture())).thenReturn(Mono.empty());

        StepVerifier.create(service.procesarLote(50)).expectNext(1).verifyComplete();

        verify(notificacionRepository, never()).marcarReintentar(any(), org.mockito.ArgumentMatchers.anyInt(), anyString(), any());
        assertThat(err.getValue()).contains("131047");
    }

    @Test
    @DisplayName("Cross-day día 0: error retryable a las 23:59:50 → marcarFallido (no reintenta cruzando de fecha)")
    void crossDayDia0_marcarFallido() {
        service = new WhatsAppQueueService(notificacionRepository, companiaRepository,
                companiaPlanRepository, whatsAppSender, clockCasiMedianoche());
        when(notificacionRepository.claimLoteWhatsapp(50)).thenReturn(Flux.just(notif(0, 0)));
        when(companiaRepository.findById(1L)).thenReturn(Mono.just(compania(true, "0987654321")));
        when(whatsAppSender.enviarPlantilla(anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.error(new WhatsAppSendException("rate limit", true, 131056)));
        ArgumentCaptor<String> err = ArgumentCaptor.forClass(String.class);
        when(notificacionRepository.marcarFallido(eq(500L), err.capture())).thenReturn(Mono.empty());

        StepVerifier.create(service.procesarLote(50)).expectNext(1).verifyComplete();

        verify(notificacionRepository, never()).marcarReintentar(any(), org.mockito.ArgumentMatchers.anyInt(), anyString(), any());
        assertThat(err.getValue()).contains("cross-day");
    }

    @Test
    @DisplayName("compañía inexistente → marcarFallido 'compania inexistente'")
    void companiaInexistente_marcarFallido() {
        when(notificacionRepository.claimLoteWhatsapp(50)).thenReturn(Flux.just(notif(3, 0)));
        when(companiaRepository.findById(1L)).thenReturn(Mono.empty());
        when(notificacionRepository.marcarFallido(eq(500L), anyString())).thenReturn(Mono.empty());

        StepVerifier.create(service.procesarLote(50)).expectNext(1).verifyComplete();

        verify(whatsAppSender, never()).enviarPlantilla(anyString(), anyString(), anyString(), any());
        verify(notificacionRepository).marcarFallido(eq(500L), eq("compania inexistente"));
    }
}
