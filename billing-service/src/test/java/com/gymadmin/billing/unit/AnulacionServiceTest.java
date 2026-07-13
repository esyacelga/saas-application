package com.gymadmin.billing.unit;

import com.gymadmin.billing.application.command.AprobarAnulacionCommand;
import com.gymadmin.billing.application.command.EmitirNotaCreditoCommand;
import com.gymadmin.billing.application.command.RechazarAnulacionCommand;
import com.gymadmin.billing.application.command.SolicitarAnulacionCommand;
import com.gymadmin.billing.application.service.AnulacionService;
import com.gymadmin.billing.application.service.CatalogoSriService;
import com.gymadmin.billing.domain.model.Anulacion;
import com.gymadmin.billing.domain.model.Comprobante;
import com.gymadmin.billing.domain.model.EstadoAnulacion;
import com.gymadmin.billing.domain.model.sri.MotivoAnulacionNcSri;
import com.gymadmin.billing.domain.port.in.NotaCreditoUseCase;
import com.gymadmin.billing.domain.port.out.AnulacionRepository;
import com.gymadmin.billing.domain.port.out.ComprobanteRepository;
import com.gymadmin.billing.domain.port.out.EmailNotificationPort;
import com.gymadmin.billing.infrastructure.exception.BusinessException;
import com.gymadmin.billing.infrastructure.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AnulacionService — G3 · máquina de estados y reglas fiscales SRI")
class AnulacionServiceTest {

    private AnulacionRepository anulacionRepository;
    private ComprobanteRepository comprobanteRepository;
    private CatalogoSriService catalogoSriService;
    private NotaCreditoUseCase notaCreditoUseCase;
    private EmailNotificationPort emailNotificationPort;

    private AnulacionService service;

    /** Reloj fijo: 2026-08-07 (día 7 del mes siguiente a facturas de 2026-07). */
    private static final LocalDate HOY = LocalDate.of(2026, 8, 7);
    private static final Integer COMPANIA_A = 1;
    private static final Integer COMPANIA_B = 2;

    @BeforeEach
    void setUp() {
        anulacionRepository = mock(AnulacionRepository.class);
        comprobanteRepository = mock(ComprobanteRepository.class);
        catalogoSriService = mock(CatalogoSriService.class);
        notaCreditoUseCase = mock(NotaCreditoUseCase.class);
        emailNotificationPort = mock(EmailNotificationPort.class);

        Clock clock = Clock.fixed(HOY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneId.of("UTC"));

        service = new AnulacionService(
                anulacionRepository,
                comprobanteRepository,
                catalogoSriService,
                notaCreditoUseCase,
                emailNotificationPort,
                clock);

        // Defaults benignos
        when(catalogoSriService.obtenerMotivoAnulacion("DEVOLUCION"))
                .thenReturn(Mono.just(new MotivoAnulacionNcSri(1, "DEVOLUCION", "Devolución de mercadería")));
        when(emailNotificationPort.enviarSolicitudAprobada(any(), any())).thenReturn(Mono.empty());
        when(emailNotificationPort.enviarSolicitudRechazada(any(), any())).thenReturn(Mono.empty());
        when(emailNotificationPort.enviarNotaCreditoAceptacion(any(), any())).thenReturn(Mono.empty());
    }

    // ------------------------------------------------------------------
    // solicitar — validaciones fiscales
    // ------------------------------------------------------------------

    @Test
    @DisplayName("solicitar: ventana temporal OK — factura 2026-07-15 con hoy 2026-08-07 se acepta")
    void solicitar_dentroDeVentana_ok() {
        Comprobante factura = facturaAutorizada(100L, COMPANIA_A, LocalDate.of(2026, 7, 15), "1712345678");
        when(comprobanteRepository.findById(100L)).thenReturn(Mono.just(factura));
        when(anulacionRepository.save(any(Anulacion.class))).thenAnswer(inv -> {
            Anulacion a = inv.getArgument(0, Anulacion.class);
            a.setId(500L);
            return Mono.just(a);
        });

        StepVerifier.create(service.solicitar(new SolicitarAnulacionCommand(
                        100L, COMPANIA_A, "Cliente devolvió el cobro", null, false, 999)))
                .assertNext(a -> {
                    assertThat(a.getEstado()).isEqualTo(EstadoAnulacion.SOLICITADA);
                    assertThat(a.getIdComprobante()).isEqualTo(100L);
                    assertThat(a.getIdCompania()).isEqualTo(COMPANIA_A);
                    // Sin Flujo B → observación null en la respuesta
                    assertThat(a.getObservacionResolucion()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("solicitar: ventana temporal vencida — factura 2026-07-15 con hoy 2026-08-08 → 422")
    void solicitar_fueraDeVentana_422() {
        // Fabricamos un service con reloj en 2026-08-08 (1 día después del límite)
        Clock clockVencido = Clock.fixed(LocalDate.of(2026, 8, 8).atStartOfDay().toInstant(ZoneOffset.UTC), ZoneId.of("UTC"));
        AnulacionService serviceVencido = new AnulacionService(
                anulacionRepository, comprobanteRepository, catalogoSriService,
                notaCreditoUseCase, emailNotificationPort, clockVencido);
        Comprobante factura = facturaAutorizada(100L, COMPANIA_A, LocalDate.of(2026, 7, 15), "1712345678");
        when(comprobanteRepository.findById(100L)).thenReturn(Mono.just(factura));

        StepVerifier.create(serviceVencido.solicitar(new SolicitarAnulacionCommand(
                        100L, COMPANIA_A, "Motivo valido", null, false, 999)))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(BusinessException.class);
                    assertThat(e.getMessage()).contains("Fuera de la ventana SRI");
                })
                .verify();

        verify(anulacionRepository, never()).save(any(Anulacion.class));
    }

    @Test
    @DisplayName("solicitar: consumidor final (9999999999999) → 422")
    void solicitar_consumidorFinal_422() {
        Comprobante factura = facturaAutorizada(100L, COMPANIA_A, LocalDate.of(2026, 7, 15), "9999999999999");
        when(comprobanteRepository.findById(100L)).thenReturn(Mono.just(factura));

        StepVerifier.create(service.solicitar(new SolicitarAnulacionCommand(
                        100L, COMPANIA_A, "Motivo valido", null, false, 999)))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(BusinessException.class);
                    assertThat(e.getMessage()).contains("consumidor final");
                })
                .verify();
    }

    @Test
    @DisplayName("solicitar: estado inválido (ANULADO) → 422")
    void solicitar_estadoInvalido_422() {
        Comprobante factura = facturaAutorizada(100L, COMPANIA_A, LocalDate.of(2026, 7, 15), "1712345678");
        factura.setEstado("ANULADO");
        when(comprobanteRepository.findById(100L)).thenReturn(Mono.just(factura));

        StepVerifier.create(service.solicitar(new SolicitarAnulacionCommand(
                        100L, COMPANIA_A, "Motivo valido", null, false, 999)))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(BusinessException.class);
                    assertThat(e.getMessage()).contains("ANULADO");
                })
                .verify();
    }

    @Test
    @DisplayName("solicitar: motivo blank → 422")
    void solicitar_motivoBlank_422() {
        StepVerifier.create(service.solicitar(new SolicitarAnulacionCommand(
                        100L, COMPANIA_A, "   ", null, false, 999)))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(BusinessException.class);
                    assertThat(e.getMessage()).contains("obligatorio");
                })
                .verify();

        verify(comprobanteRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("solicitar: motivo no reconocido en catálogo → 404 (propagado del CatalogoSriService)")
    void solicitar_motivoInexistente_404() {
        Comprobante factura = facturaAutorizada(100L, COMPANIA_A, LocalDate.of(2026, 7, 15), "1712345678");
        when(comprobanteRepository.findById(100L)).thenReturn(Mono.just(factura));
        when(catalogoSriService.obtenerMotivoAnulacion("NO_EXISTE"))
                .thenReturn(Mono.error(new NotFoundException("Motivo de anulación no reconocido: NO_EXISTE")));

        StepVerifier.create(service.solicitar(new SolicitarAnulacionCommand(
                        100L, COMPANIA_A, "Motivo valido", "NO_EXISTE", false, 999)))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("multi-tenant: solicitar sobre factura de otra compañía → 404")
    void solicitar_multiTenant_facturaOtraCompania_404() {
        Comprobante factura = facturaAutorizada(100L, COMPANIA_B, LocalDate.of(2026, 7, 15), "1712345678");
        when(comprobanteRepository.findById(100L)).thenReturn(Mono.just(factura));

        StepVerifier.create(service.solicitar(new SolicitarAnulacionCommand(
                        100L, COMPANIA_A, "Motivo valido", null, false, 999)))
                .expectError(NotFoundException.class)
                .verify();

        verify(anulacionRepository, never()).save(any(Anulacion.class));
    }

    @Test
    @DisplayName("solicitar Flujo B: persiste flag [FLUJO_B] + [MOTIVO=CODIGO] en observacion_resolucion")
    void solicitar_flujoB_persisteMetadataEnObservacion() {
        Comprobante factura = facturaAutorizada(100L, COMPANIA_A, LocalDate.of(2026, 7, 15), "1712345678");
        when(comprobanteRepository.findById(100L)).thenReturn(Mono.just(factura));
        ArgumentCaptor<Anulacion> captor = ArgumentCaptor.forClass(Anulacion.class);
        when(anulacionRepository.save(captor.capture())).thenAnswer(inv -> {
            Anulacion a = inv.getArgument(0, Anulacion.class);
            a.setId(500L);
            return Mono.just(a);
        });

        StepVerifier.create(service.solicitar(new SolicitarAnulacionCommand(
                        100L, COMPANIA_A, "Devolucion completa", "DEVOLUCION", true, 999)))
                .expectNextCount(1)
                .verifyComplete();

        Anulacion persistida = captor.getValue();
        assertThat(persistida.getObservacionResolucion())
                .isNotNull()
                .contains("[FLUJO_B]")
                .contains("[MOTIVO=DEVOLUCION]");
    }

    // ------------------------------------------------------------------
    // aprobar
    // ------------------------------------------------------------------

    @Test
    @DisplayName("aprobar: Flujo A SOLICITADA → APROBADA (sin NC)")
    void aprobar_flujoA_solicitadaAAprobada() {
        Anulacion solicitada = anulacionSolicitada(500L, COMPANIA_A, 100L, null);
        when(anulacionRepository.findById(500L)).thenReturn(Mono.just(solicitada));
        Comprobante factura = facturaAutorizada(100L, COMPANIA_A, LocalDate.of(2026, 7, 15), "1712345678");
        when(comprobanteRepository.findById(100L)).thenReturn(Mono.just(factura));
        Anulacion aprobada = solicitada.toBuilder()
                .estado(EstadoAnulacion.APROBADA)
                .idUsuarioAprueba(777)
                .build();
        when(anulacionRepository.updateEstado(eq(500L), eq(EstadoAnulacion.APROBADA), any(), any(), any(), any()))
                .thenReturn(Mono.just(aprobada));

        StepVerifier.create(service.aprobar(new AprobarAnulacionCommand(500L, COMPANIA_A, 777, null)))
                .assertNext(a -> assertThat(a.getEstado()).isEqualTo(EstadoAnulacion.APROBADA))
                .verifyComplete();

        // Flujo A: no debe llamar a NC
        verify(notaCreditoUseCase, never()).emitirNotaCredito(any());
    }

    @Test
    @DisplayName("aprobar: Flujo B con NC AUTORIZADA → APROBADA → EJECUTADA + comprobante ANULADO")
    void aprobar_flujoB_ncAutorizada_ejecuta() {
        Anulacion solicitada = anulacionSolicitada(500L, COMPANIA_A, 100L, "[FLUJO_B][MOTIVO=DEVOLUCION]");
        when(anulacionRepository.findById(500L)).thenReturn(Mono.just(solicitada));
        Comprobante factura = facturaAutorizada(100L, COMPANIA_A, LocalDate.of(2026, 7, 15), "1712345678");
        factura.setTotal(new BigDecimal("30.00"));
        when(comprobanteRepository.findById(100L)).thenReturn(Mono.just(factura));
        when(comprobanteRepository.findDetallesByIdComprobante(100L)).thenReturn(Flux.empty());
        Anulacion aprobada = solicitada.toBuilder().estado(EstadoAnulacion.APROBADA).build();
        Anulacion ejecutada = solicitada.toBuilder().estado(EstadoAnulacion.EJECUTADA).idComprobanteNc(999L).build();
        when(anulacionRepository.updateEstado(eq(500L), eq(EstadoAnulacion.APROBADA), any(), any(), any(), any()))
                .thenReturn(Mono.just(aprobada));
        when(anulacionRepository.updateEstado(eq(500L), eq(EstadoAnulacion.EJECUTADA), any(), any(), any(), eq(999L)))
                .thenReturn(Mono.just(ejecutada));

        Comprobante nc = Comprobante.builder().id(999L).estado("AUTORIZADO").build();
        when(notaCreditoUseCase.emitirNotaCredito(any(EmitirNotaCreditoCommand.class))).thenReturn(Mono.just(nc));
        when(comprobanteRepository.updateEstado(eq(100L), eq("ANULADO"), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(factura));

        StepVerifier.create(service.aprobar(new AprobarAnulacionCommand(500L, COMPANIA_A, 777, null)))
                .assertNext(a -> {
                    assertThat(a.getEstado()).isEqualTo(EstadoAnulacion.EJECUTADA);
                    assertThat(a.getIdComprobanteNc()).isEqualTo(999L);
                })
                .verifyComplete();

        verify(notaCreditoUseCase).emitirNotaCredito(any());
        verify(comprobanteRepository).updateEstado(eq(100L), eq("ANULADO"), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("aprobar: Flujo B con NC DEVUELTO → queda en APROBADA (no EJECUTADA)")
    void aprobar_flujoB_ncPendiente_quedaAprobada() {
        Anulacion solicitada = anulacionSolicitada(500L, COMPANIA_A, 100L, "[FLUJO_B][MOTIVO=DEVOLUCION]");
        when(anulacionRepository.findById(500L)).thenReturn(Mono.just(solicitada));
        Comprobante factura = facturaAutorizada(100L, COMPANIA_A, LocalDate.of(2026, 7, 15), "1712345678");
        factura.setTotal(new BigDecimal("30.00"));
        when(comprobanteRepository.findById(100L)).thenReturn(Mono.just(factura));
        when(comprobanteRepository.findDetallesByIdComprobante(100L)).thenReturn(Flux.empty());
        Anulacion aprobada = solicitada.toBuilder().estado(EstadoAnulacion.APROBADA).build();
        when(anulacionRepository.updateEstado(eq(500L), eq(EstadoAnulacion.APROBADA), any(), any(), any(), any()))
                .thenReturn(Mono.just(aprobada))
                .thenReturn(Mono.just(aprobada.toBuilder().idComprobanteNc(999L).build()));

        Comprobante nc = Comprobante.builder().id(999L).estado("DEVUELTO").build();
        when(notaCreditoUseCase.emitirNotaCredito(any(EmitirNotaCreditoCommand.class))).thenReturn(Mono.just(nc));

        StepVerifier.create(service.aprobar(new AprobarAnulacionCommand(500L, COMPANIA_A, 777, null)))
                .assertNext(a -> assertThat(a.getEstado()).isEqualTo(EstadoAnulacion.APROBADA))
                .verifyComplete();

        // El comprobante original NO se marca ANULADO hasta que la NC autorice
        verify(comprobanteRepository, never())
                .updateEstado(eq(100L), eq("ANULADO"), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("aprobar: transición inválida (ya APROBADA) → 422")
    void aprobar_estadoInvalido_422() {
        Anulacion yaAprobada = anulacionSolicitada(500L, COMPANIA_A, 100L, null)
                .toBuilder().estado(EstadoAnulacion.APROBADA).build();
        when(anulacionRepository.findById(500L)).thenReturn(Mono.just(yaAprobada));

        StepVerifier.create(service.aprobar(new AprobarAnulacionCommand(500L, COMPANIA_A, 777, null)))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("multi-tenant: aprobar anulación de otra compañía → 404")
    void aprobar_multiTenant_404() {
        Anulacion otraCompania = anulacionSolicitada(500L, COMPANIA_B, 100L, null);
        when(anulacionRepository.findById(500L)).thenReturn(Mono.just(otraCompania));

        StepVerifier.create(service.aprobar(new AprobarAnulacionCommand(500L, COMPANIA_A, 777, null)))
                .expectError(NotFoundException.class)
                .verify();
    }

    // ------------------------------------------------------------------
    // rechazar
    // ------------------------------------------------------------------

    @Test
    @DisplayName("rechazar: SOLICITADA → RECHAZADA persistiendo observación")
    void rechazar_solicitadaARechazada() {
        Anulacion solicitada = anulacionSolicitada(500L, COMPANIA_A, 100L, null);
        when(anulacionRepository.findById(500L)).thenReturn(Mono.just(solicitada));
        when(comprobanteRepository.findById(100L)).thenReturn(Mono.just(
                facturaAutorizada(100L, COMPANIA_A, LocalDate.of(2026, 7, 15), "1712345678")));
        Anulacion rechazada = solicitada.toBuilder()
                .estado(EstadoAnulacion.RECHAZADA)
                .observacionResolucion("No procede — cliente ya utilizó el servicio")
                .build();
        when(anulacionRepository.updateEstado(eq(500L), eq(EstadoAnulacion.RECHAZADA), any(), any(), any(), any()))
                .thenReturn(Mono.just(rechazada));

        StepVerifier.create(service.rechazar(new RechazarAnulacionCommand(
                        500L, COMPANIA_A, 777, "No procede — cliente ya utilizó el servicio")))
                .assertNext(a -> {
                    assertThat(a.getEstado()).isEqualTo(EstadoAnulacion.RECHAZADA);
                    assertThat(a.getObservacionResolucion()).contains("No procede");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("rechazar: observación en blanco → 422")
    void rechazar_observacionEnBlanco_422() {
        StepVerifier.create(service.rechazar(new RechazarAnulacionCommand(500L, COMPANIA_A, 777, "  ")))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(BusinessException.class);
                    assertThat(e.getMessage()).contains("observación es obligatoria");
                })
                .verify();
    }

    // ------------------------------------------------------------------
    // confirmarSri
    // ------------------------------------------------------------------

    @Test
    @DisplayName("confirmarSri: Flujo A APROBADA → EJECUTADA + comprobante ANULADO")
    void confirmarSri_aprobadaAEjecutada() {
        Anulacion aprobada = anulacionSolicitada(500L, COMPANIA_A, 100L, null)
                .toBuilder().estado(EstadoAnulacion.APROBADA).build();
        when(anulacionRepository.findById(500L)).thenReturn(Mono.just(aprobada));
        Anulacion ejecutada = aprobada.toBuilder().estado(EstadoAnulacion.EJECUTADA).build();
        when(anulacionRepository.updateEstado(eq(500L), eq(EstadoAnulacion.EJECUTADA), any(), any(), any(), any()))
                .thenReturn(Mono.just(ejecutada));
        Comprobante factura = facturaAutorizada(100L, COMPANIA_A, LocalDate.of(2026, 7, 15), "1712345678");
        when(comprobanteRepository.updateEstado(eq(100L), eq("ANULADO"), any(), any(), any(), any(), any()))
                .thenReturn(Mono.just(factura));

        StepVerifier.create(service.confirmarSri(500L, COMPANIA_A, 777))
                .assertNext(a -> assertThat(a.getEstado()).isEqualTo(EstadoAnulacion.EJECUTADA))
                .verifyComplete();

        verify(comprobanteRepository).updateEstado(eq(100L), eq("ANULADO"), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("confirmarSri: estado SOLICITADA → 422 (falta aprobación)")
    void confirmarSri_desdeSolicitada_422() {
        Anulacion solicitada = anulacionSolicitada(500L, COMPANIA_A, 100L, null);
        when(anulacionRepository.findById(500L)).thenReturn(Mono.just(solicitada));

        StepVerifier.create(service.confirmarSri(500L, COMPANIA_A, 777))
                .expectError(BusinessException.class)
                .verify();
    }

    // ------------------------------------------------------------------
    // buscarPorId — multi-tenant
    // ------------------------------------------------------------------

    @Test
    @DisplayName("buscarPorId: multi-tenant otra compañía → 404 y observación strippeada")
    void buscarPorId_multiTenant_404() {
        Anulacion a = anulacionSolicitada(500L, COMPANIA_B, 100L, "[FLUJO_B][MOTIVO=DEVOLUCION]");
        when(anulacionRepository.findById(500L)).thenReturn(Mono.just(a));

        StepVerifier.create(service.buscarPorId(500L, COMPANIA_A))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("buscarPorId: misma compañía devuelve observación sin metadata interna")
    void buscarPorId_mismaCompania_observacionStrippeada() {
        Anulacion a = anulacionSolicitada(500L, COMPANIA_A, 100L, "[FLUJO_B][MOTIVO=DEVOLUCION] Cliente pidió devolucion");
        when(anulacionRepository.findById(500L)).thenReturn(Mono.just(a));

        StepVerifier.create(service.buscarPorId(500L, COMPANIA_A))
                .assertNext(res -> {
                    assertThat(res.getObservacionResolucion())
                            .doesNotContain("[FLUJO_B]")
                            .doesNotContain("[MOTIVO=")
                            .contains("Cliente pidió devolucion");
                })
                .verifyComplete();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Comprobante facturaAutorizada(Long id, Integer idCompania, LocalDate fechaEmision, String idReceptor) {
        return Comprobante.builder()
                .id(id)
                .idCompania(idCompania)
                .idSucursal(1)
                .tipoComprobante("01")
                .estado("AUTORIZADO")
                .codEstablecimiento("001")
                .codPuntoEmision("001")
                .secuencial("000000123")
                .fechaEmision(fechaEmision)
                .tipoIdReceptor("05")
                .idReceptor(idReceptor)
                .razonSocialReceptor("Cliente Original")
                .emailReceptor("cliente@test.local")
                .total(new BigDecimal("30.00"))
                .build();
    }

    private Anulacion anulacionSolicitada(Long id, Integer idCompania, Long idComprobante, String observacion) {
        return Anulacion.builder()
                .id(id)
                .idCompania(idCompania)
                .idSucursal(1)
                .idComprobante(idComprobante)
                .motivo("Cliente pidió devolución")
                .estado(EstadoAnulacion.SOLICITADA)
                .idUsuarioSolicita(999)
                .fechaSolicitud(Instant.ofEpochMilli(0).atOffset(ZoneOffset.UTC))
                .observacionResolucion(observacion)
                .build();
    }
}
