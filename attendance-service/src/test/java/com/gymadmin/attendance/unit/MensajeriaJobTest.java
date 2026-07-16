package com.gymadmin.attendance.unit;

import com.gymadmin.attendance.application.service.MensajeLogService;
import com.gymadmin.attendance.domain.model.MensajeLog;
import com.gymadmin.attendance.domain.port.out.AsistenciaRepository;
import com.gymadmin.attendance.infrastructure.adapter.out.core.CoreServiceClient;
import com.gymadmin.attendance.infrastructure.adapter.out.core.CoreServiceClient.ClientePorVencer;
import com.gymadmin.attendance.infrastructure.adapter.out.platform.PlatformServiceClient;
import com.gymadmin.attendance.infrastructure.scheduler.MensajeriaJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REQ-SAAS-001 (Fase 5): unit del {@link MensajeriaJob} — ruteo de plantilla HSM por
 * {@code tipo}+{@code modoControl}, opt-in (R4), teléfono normalizable, RN-05 (congelado) e
 * idempotencia por día (C2). El {@code WhatsAppSender} real se stubea vía {@link MensajeLogService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MensajeriaJob — ruteo HSM + opt-in + idempotencia (Fase 5)")
class MensajeriaJobTest {

    private AsistenciaRepository asistenciaRepository;
    private CoreServiceClient coreServiceClient;
    private PlatformServiceClient platformServiceClient;
    private MensajeLogService mensajeLogService;
    private MensajeriaJob job;

    private static final Integer COMPANIA = 1;
    private static final String GYM = "PowerGym";

    @BeforeEach
    void setUp() {
        asistenciaRepository = mock(AsistenciaRepository.class);
        coreServiceClient = mock(CoreServiceClient.class);
        platformServiceClient = mock(PlatformServiceClient.class);
        mensajeLogService = mock(MensajeLogService.class);
        job = new MensajeriaJob(asistenciaRepository, coreServiceClient, platformServiceClient, mensajeLogService);

        // Fase 6: por defecto el bucket previo del socio = 3 (igual que el seed), conservando el ventaneo
        // {3,0} de los tests existentes. El fallback recibido (3) se devuelve tal cual salvo override.
        lenient().when(platformServiceClient.obtenerBucketPrevioSocio(anyInt()))
                .thenReturn(Mono.just(3));
        lenient().when(asistenciaRepository.findNombreCompania(COMPANIA)).thenReturn(Mono.just(GYM));
        lenient().when(mensajeLogService.existsEnviadoHoy(anyInt(), anyString(), anyString()))
                .thenReturn(Mono.just(false));
        lenient().when(mensajeLogService.enviarWhatsAppJob(anyInt(), any(), anyInt(), anyString(),
                        anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(Mono.just(new MensajeLog()));
    }

    private ClientePorVencer cliente(String modo, Integer dias, Integer accesos, String tel,
                                     boolean optIn, String estado) {
        ClientePorVencer c = mock(ClientePorVencer.class);
        lenient().when(c.getIdCliente()).thenReturn(10);
        lenient().when(c.getIdSucursal()).thenReturn(1);
        lenient().when(c.getNombre()).thenReturn("María");
        lenient().when(c.getTelefono()).thenReturn(tel);
        lenient().when(c.getModoControl()).thenReturn(modo);
        lenient().when(c.getDiasParaVencer()).thenReturn(dias);
        lenient().when(c.getAccesosRestantes()).thenReturn(accesos);
        lenient().when(c.getFechaFin()).thenReturn("2026-07-18");
        lenient().when(c.isAceptaWhatsapp()).thenReturn(optIn);
        lenient().when(c.getEstadoCliente()).thenReturn(estado);
        return c;
    }

    private void stubClientes(ClientePorVencer... clientes) {
        when(asistenciaRepository.findCompaniasActivas()).thenReturn(Flux.just(COMPANIA));
        when(coreServiceClient.listarClientesPorVencer(eq(COMPANIA), anyInt(), anyString()))
                .thenReturn(Flux.just(clientes));
    }

    @Test
    @DisplayName("Calendario vence en 3 días + opt-in → venc_membresia_previo [nombre, gym, fecha, dias]")
    void calendarioPrevio_enviaPlantillaPrevio() {
        stubClientes(cliente("calendario", 3, null, "0987654321", true, "proximo_vencer"));

        StepVerifier.create(job.procesarAusencias()).verifyComplete();

        ArgumentCaptor<List<String>> params = ArgumentCaptor.forClass(List.class);
        verify(mensajeLogService).enviarWhatsAppJob(eq(COMPANIA), eq(1), eq(10),
                eq("vencimiento_3d"), eq("whatsapp"), eq("+593987654321"),
                eq("venc_membresia_previo"), eq("es"), params.capture(), anyString());
        assertThat(params.getValue()).containsExactly("María", GYM, "18/07/2026", "3");
    }

    @Test
    @DisplayName("Calendario vence hoy (0) → venc_membresia_hoy [nombre, gym]")
    void calendarioHoy_enviaPlantillaHoy() {
        stubClientes(cliente("calendario", 0, null, "0987654321", true, "proximo_vencer"));

        StepVerifier.create(job.procesarAusencias()).verifyComplete();

        verify(mensajeLogService).enviarWhatsAppJob(eq(COMPANIA), eq(1), eq(10),
                eq("vencimiento_hoy"), eq("whatsapp"), eq("+593987654321"),
                eq("venc_membresia_hoy"), eq("es"), eq(List.of("María", GYM)), anyString());
    }

    @Test
    @DisplayName("Accesos restantes 3 → venc_accesos_previo [nombre, accesos, gym]")
    void accesosPrevio_enviaPlantillaAccesosPrevio() {
        stubClientes(cliente("accesos", null, 3, "0987654321", true, "proximo_vencer"));

        StepVerifier.create(job.procesarAusencias()).verifyComplete();

        verify(mensajeLogService).enviarWhatsAppJob(eq(COMPANIA), eq(1), eq(10),
                eq("vencimiento_3d"), eq("whatsapp"), eq("+593987654321"),
                eq("venc_accesos_previo"), eq("es"), eq(List.of("María", "3", GYM)), anyString());
    }

    @Test
    @DisplayName("Accesos restantes 0 → venc_accesos_final [nombre, gym]")
    void accesosFinal_enviaPlantillaAccesosFinal() {
        stubClientes(cliente("accesos", null, 0, "0987654321", true, "proximo_vencer"));

        StepVerifier.create(job.procesarAusencias()).verifyComplete();

        verify(mensajeLogService).enviarWhatsAppJob(eq(COMPANIA), eq(1), eq(10),
                eq("vencimiento_hoy"), eq("whatsapp"), eq("+593987654321"),
                eq("venc_accesos_final"), eq("es"), eq(List.of("María", GYM)), anyString());
    }

    @Test
    @DisplayName("Sin opt-in → NO se envía WhatsApp (R4)")
    void sinOptIn_noEnvia() {
        stubClientes(cliente("calendario", 3, null, "0987654321", false, "proximo_vencer"));

        StepVerifier.create(job.procesarAusencias()).verifyComplete();

        verify(mensajeLogService, never()).enviarWhatsAppJob(anyInt(), any(), anyInt(), anyString(),
                anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("Teléfono no normalizable → skip telefono_invalido, no envía")
    void telefonoInvalido_noEnvia() {
        stubClientes(cliente("calendario", 3, null, "123", true, "proximo_vencer"));

        StepVerifier.create(job.procesarAusencias()).verifyComplete();

        verify(mensajeLogService, never()).enviarWhatsAppJob(anyInt(), any(), anyInt(), anyString(),
                anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("Congelado → NO se envía (RN-05)")
    void congelado_noEnvia() {
        stubClientes(cliente("calendario", 0, null, "0987654321", true, "congelado"));

        StepVerifier.create(job.procesarAusencias()).verifyComplete();

        verify(mensajeLogService, never()).enviarWhatsAppJob(anyInt(), any(), anyInt(), anyString(),
                anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("Idempotencia C2: ya enviado hoy → no reenvía")
    void yaEnviadoHoy_noReenvia() {
        stubClientes(cliente("calendario", 3, null, "0987654321", true, "proximo_vencer"));
        when(mensajeLogService.existsEnviadoHoy(eq(10), eq("vencimiento_3d"), eq("whatsapp")))
                .thenReturn(Mono.just(true));

        StepVerifier.create(job.procesarAusencias()).verifyComplete();

        verify(mensajeLogService, never()).enviarWhatsAppJob(anyInt(), any(), anyInt(), anyString(),
                anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("Fuera de bucket (calendario 5 días) → no envía")
    void fueraDeBucket_noEnvia() {
        stubClientes(cliente("calendario", 5, null, "0987654321", true, "proximo_vencer"));

        StepVerifier.create(job.procesarAusencias()).verifyComplete();

        verify(mensajeLogService, never()).enviarWhatsAppJob(anyInt(), any(), anyInt(), anyString(),
                anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    @DisplayName("Fase 6: bucket previo dinámico = 7 → calendario 5 días AHORA sí envía previo")
    void bucketPrevioDinamico7_calendario5Envia() {
        when(platformServiceClient.obtenerBucketPrevioSocio(anyInt())).thenReturn(Mono.just(7));
        stubClientes(cliente("calendario", 5, null, "0987654321", true, "proximo_vencer"));

        StepVerifier.create(job.procesarAusencias()).verifyComplete();

        ArgumentCaptor<List<String>> params = ArgumentCaptor.forClass(List.class);
        verify(mensajeLogService).enviarWhatsAppJob(eq(COMPANIA), eq(1), eq(10),
                eq("vencimiento_3d"), eq("whatsapp"), eq("+593987654321"),
                eq("venc_membresia_previo"), eq("es"), params.capture(), anyString());
        assertThat(params.getValue()).containsExactly("María", GYM, "18/07/2026", "5");
    }

    @Test
    @DisplayName("Fase 6: bucket previo = 0 (desactivado) → previo omitido, pero día 0 SÍ dispara")
    void bucketPrevioDesactivado_previoOmitido_dia0Envia() {
        when(platformServiceClient.obtenerBucketPrevioSocio(anyInt())).thenReturn(Mono.just(0));
        // Un socio a 3 días queda fuera; uno a 0 días (vence hoy) SÍ dispara.
        stubClientes(
                cliente("calendario", 3, null, "0987654321", true, "proximo_vencer"),
                clienteHoy());

        StepVerifier.create(job.procesarAusencias()).verifyComplete();

        // Solo el de "vence hoy" debe salir (venc_membresia_hoy).
        verify(mensajeLogService).enviarWhatsAppJob(eq(COMPANIA), anyInt(), eq(20),
                eq("vencimiento_hoy"), eq("whatsapp"), eq("+593999888777"),
                eq("venc_membresia_hoy"), eq("es"), eq(List.of("Juan", GYM)), anyString());
        verify(mensajeLogService, never()).enviarWhatsAppJob(anyInt(), any(), eq(10), anyString(),
                anyString(), anyString(), anyString(), anyString(), any(), any());
    }

    /** Socio distinto (id 20) que vence hoy, para el caso de bucket previo desactivado. */
    private ClientePorVencer clienteHoy() {
        ClientePorVencer c = mock(ClientePorVencer.class);
        lenient().when(c.getIdCliente()).thenReturn(20);
        lenient().when(c.getIdSucursal()).thenReturn(1);
        lenient().when(c.getNombre()).thenReturn("Juan");
        lenient().when(c.getTelefono()).thenReturn("0999888777");
        lenient().when(c.getModoControl()).thenReturn("calendario");
        lenient().when(c.getDiasParaVencer()).thenReturn(0);
        lenient().when(c.getAccesosRestantes()).thenReturn(null);
        lenient().when(c.getFechaFin()).thenReturn("2026-07-15");
        lenient().when(c.isAceptaWhatsapp()).thenReturn(true);
        lenient().when(c.getEstadoCliente()).thenReturn("proximo_vencer");
        return c;
    }
}
