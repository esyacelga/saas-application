package com.gymadmin.platform.unit;

import com.gymadmin.platform.application.service.EmailQueueService;
import com.gymadmin.platform.domain.model.Compania;
import com.gymadmin.platform.domain.model.NotificacionSuscripcion;
import com.gymadmin.platform.domain.port.in.ActividadPlataformaUseCase;
import com.gymadmin.platform.domain.port.in.EnviarNotificacionUseCase.EncolarNotificacionCommand;
import com.gymadmin.platform.domain.port.out.CompaniaRepository;
import com.gymadmin.platform.domain.port.out.EmailSender;
import com.gymadmin.platform.domain.port.out.NotificacionRepository;
import com.gymadmin.platform.infrastructure.email.EmailTemplateEngine;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REQ-SAAS-001 (Sub-fase 1.5): cubre el flujo de {@link EmailQueueService}:
 * <ol>
 *   <li>Envío exitoso → estado enviado + evento NOTIF_VENCIMIENTO_ENVIADA.</li>
 *   <li>Fallo con intentos &lt; MAX → estado reintentar + backoff exponencial.</li>
 *   <li>Fallo con intentos &gt;= MAX → estado fallido + evento NOTIF_EMAIL_FALLIDA.</li>
 *   <li>Encolar → persiste con estado=pendiente, intentos=0.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EmailQueueService — cola de emails con retry backoff")
class EmailQueueServiceTest {

    @Mock NotificacionRepository notificacionRepository;
    @Mock CompaniaRepository companiaRepository;
    @Mock EmailSender emailSender;
    @Mock ActividadPlataformaUseCase actividadUseCase;

    private final Clock clockFijo = Clock.fixed(
            OffsetDateTime.of(2026, 7, 10, 12, 0, 0, 0, ZoneOffset.UTC).toInstant(),
            ZoneOffset.UTC);
    private final EmailTemplateEngine templateEngine = new EmailTemplateEngine();

    private EmailQueueService service;

    @BeforeEach
    void setUp() {
        service = new EmailQueueService(
                notificacionRepository, companiaRepository, emailSender, templateEngine,
                actividadUseCase, clockFijo,
                "https://x.test/planes", "https://x.test");
    }

    private NotificacionSuscripcion buildNotif(int intentos) {
        NotificacionSuscripcion n = new NotificacionSuscripcion();
        n.setId(42L);
        n.setIdCompania(1L);
        n.setIdCompaniaPlan(100L);
        n.setTipo("VENCIMIENTO_TRIAL");
        n.setDiasAntes(7);
        n.setCanal(NotificacionSuscripcion.CANAL_EMAIL);
        n.setEstado(NotificacionSuscripcion.ESTADO_PENDIENTE);
        n.setIntentos(intentos);
        return n;
    }

    private Compania buildCompania() {
        Compania c = new Compania();
        c.setId(1L);
        c.setNombre("GymFit");
        c.setCorreo("owner@x.test");
        return c;
    }

    @Test
    @DisplayName("envío exitoso → marcarEnviado + evento NOTIF_VENCIMIENTO_ENVIADA")
    void envioExitoso() {
        NotificacionSuscripcion notif = buildNotif(0);
        when(notificacionRepository.claimLoteEmails(anyInt())).thenReturn(Flux.just(notif));
        when(companiaRepository.findById(1L)).thenReturn(Mono.just(buildCompania()));
        when(emailSender.enviar(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty());
        when(notificacionRepository.marcarEnviado(42L)).thenReturn(Mono.empty());
        when(actividadUseCase.registrar(any(ActividadPlataformaUseCase.RegistrarActividadCommand.class)))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.procesarLote(10))
                .expectNext(1)
                .verifyComplete();

        verify(notificacionRepository).marcarEnviado(42L);
        ArgumentCaptor<ActividadPlataformaUseCase.RegistrarActividadCommand> cap =
                ArgumentCaptor.forClass(ActividadPlataformaUseCase.RegistrarActividadCommand.class);
        verify(actividadUseCase).registrar(cap.capture());
        assertThat(cap.getValue().evento()).isEqualTo("NOTIF_VENCIMIENTO_ENVIADA");
        verify(notificacionRepository, never())
                .marcarReintentar(anyLong(), anyInt(), anyString(), any());
        verify(notificacionRepository, never()).marcarFallido(anyLong(), anyString());
    }

    @Test
    @DisplayName("primer fallo (intentos=0) → reintentar + backoff 30s")
    void fallaProgramaReintento30s() {
        NotificacionSuscripcion notif = buildNotif(0);
        when(notificacionRepository.claimLoteEmails(anyInt())).thenReturn(Flux.just(notif));
        when(companiaRepository.findById(1L)).thenReturn(Mono.just(buildCompania()));
        when(emailSender.enviar(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("SMTP down")));
        when(notificacionRepository.marcarReintentar(eq(42L), eq(1), anyString(), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.procesarLote(10))
                .expectNext(1)
                .verifyComplete();

        ArgumentCaptor<OffsetDateTime> proximoCap = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(notificacionRepository).marcarReintentar(eq(42L), eq(1), eq("SMTP down"), proximoCap.capture());
        OffsetDateTime esperado = OffsetDateTime.now(clockFijo).plusSeconds(30);
        assertThat(proximoCap.getValue()).isEqualTo(esperado);
    }

    @Test
    @DisplayName("4º fallo (intentos=3 previos) → marcarFallido + evento NOTIF_EMAIL_FALLIDA")
    void falloDefinitivoTrasMaxIntentos() {
        NotificacionSuscripcion notif = buildNotif(3);
        when(notificacionRepository.claimLoteEmails(anyInt())).thenReturn(Flux.just(notif));
        when(companiaRepository.findById(1L)).thenReturn(Mono.just(buildCompania()));
        when(emailSender.enviar(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("SMTP hard error")));
        when(notificacionRepository.marcarFallido(eq(42L), anyString())).thenReturn(Mono.empty());
        when(actividadUseCase.registrar(any(ActividadPlataformaUseCase.RegistrarActividadCommand.class)))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.procesarLote(10))
                .expectNext(1)
                .verifyComplete();

        verify(notificacionRepository).marcarFallido(42L, "SMTP hard error");
        ArgumentCaptor<ActividadPlataformaUseCase.RegistrarActividadCommand> cap =
                ArgumentCaptor.forClass(ActividadPlataformaUseCase.RegistrarActividadCommand.class);
        verify(actividadUseCase).registrar(cap.capture());
        assertThat(cap.getValue().evento()).isEqualTo("NOTIF_EMAIL_FALLIDA");
        assertThat(cap.getValue().detalle()).containsKey("id_notif");
    }

    @Test
    @DisplayName("encolar → guarda notificación con estado pendiente, intentos=0")
    void encolarPersistePendiente() {
        ArgumentCaptor<NotificacionSuscripcion> cap = ArgumentCaptor.forClass(NotificacionSuscripcion.class);
        when(notificacionRepository.save(cap.capture())).thenAnswer(inv -> {
            NotificacionSuscripcion arg = inv.getArgument(0);
            arg.setId(555L);
            return Mono.just(arg);
        });

        EncolarNotificacionCommand cmd = new EncolarNotificacionCommand(
                1L, 100L, "VENCIMIENTO_TRIAL", 7, NotificacionSuscripcion.CANAL_EMAIL,
                "vencimiento_7d", Map.of(), "owner@x.test");

        StepVerifier.create(service.encolar(cmd))
                .expectNext(555L)
                .verifyComplete();

        NotificacionSuscripcion saved = cap.getValue();
        assertThat(saved.getEstado()).isEqualTo(NotificacionSuscripcion.ESTADO_PENDIENTE);
        assertThat(saved.getIntentos()).isEqualTo(0);
        assertThat(saved.getCanal()).isEqualTo("email");
        assertThat(saved.getTipo()).isEqualTo("VENCIMIENTO_TRIAL");
        assertThat(saved.getProximoIntento()).isEqualTo(OffsetDateTime.now(clockFijo));
    }
}
