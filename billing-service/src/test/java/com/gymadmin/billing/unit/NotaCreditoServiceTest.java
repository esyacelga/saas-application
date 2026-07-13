package com.gymadmin.billing.unit;

import com.gymadmin.billing.application.command.EmitirFacturaCommand;
import com.gymadmin.billing.application.command.EmitirNotaCreditoCommand;
import com.gymadmin.billing.application.service.CatalogoSriService;
import com.gymadmin.billing.application.service.EnvioSriService;
import com.gymadmin.billing.application.service.NotaCreditoService;
import com.gymadmin.billing.domain.model.Comprobante;
import com.gymadmin.billing.domain.model.ConfigSri;
import com.gymadmin.billing.domain.model.NotaCreditoReferencia;
import com.gymadmin.billing.domain.model.sri.MotivoAnulacionNcSri;
import com.gymadmin.billing.domain.port.out.ComprobanteRepository;
import com.gymadmin.billing.domain.port.out.ConfigSriRepository;
import com.gymadmin.billing.domain.port.out.NotaCreditoReferenciaRepository;
import com.gymadmin.billing.domain.port.out.SecuencialRepository;
import com.gymadmin.billing.infrastructure.adapter.out.xml.NotaCreditoXmlBuilder;
import com.gymadmin.billing.infrastructure.exception.BusinessException;
import com.gymadmin.billing.infrastructure.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("NotaCreditoService.emitirNotaCredito — validaciones + G2 pipeline (G4)")
class NotaCreditoServiceTest {

    private ComprobanteRepository comprobanteRepository;
    private ConfigSriRepository configSriRepository;
    private SecuencialRepository secuencialRepository;
    private CatalogoSriService catalogoSriService;
    private NotaCreditoReferenciaRepository notaCreditoReferenciaRepository;
    private NotaCreditoXmlBuilder notaCreditoXmlBuilder;
    private EnvioSriService envioSriService;

    private NotaCreditoService service;

    @BeforeEach
    void setUp() {
        comprobanteRepository = mock(ComprobanteRepository.class);
        configSriRepository = mock(ConfigSriRepository.class);
        secuencialRepository = mock(SecuencialRepository.class);
        catalogoSriService = mock(CatalogoSriService.class);
        notaCreditoReferenciaRepository = mock(NotaCreditoReferenciaRepository.class);
        notaCreditoXmlBuilder = mock(NotaCreditoXmlBuilder.class);
        envioSriService = mock(EnvioSriService.class);

        service = new NotaCreditoService(
                comprobanteRepository,
                configSriRepository,
                secuencialRepository,
                catalogoSriService,
                notaCreditoReferenciaRepository,
                notaCreditoXmlBuilder,
                envioSriService);

        // Defaults happy path.
        when(catalogoSriService.obtenerMotivoAnulacion("DEVOLUCION"))
                .thenReturn(Mono.just(new MotivoAnulacionNcSri(1, "DEVOLUCION", "Devolución de mercadería")));
        when(configSriRepository.findByEmpresa(1, 1))
                .thenReturn(Mono.just(defaultConfigSri()));
        when(secuencialRepository.reservarSiguiente(1, 1, "001", "001", "04"))
                .thenReturn(Mono.just(42));
        when(notaCreditoXmlBuilder.buildXml(any(), any(), any(), any(), any()))
                .thenReturn("<notaCredito>...</notaCredito>");
        when(notaCreditoReferenciaRepository.save(any(NotaCreditoReferencia.class)))
                .thenAnswer(inv -> {
                    NotaCreditoReferencia r = inv.getArgument(0, NotaCreditoReferencia.class);
                    r.setId(500L);
                    return Mono.just(r);
                });
        when(envioSriService.procesarEmisionInmediataConXml(any(Comprobante.class), anyString()))
                .thenAnswer(inv -> {
                    Comprobante c = inv.getArgument(0, Comprobante.class);
                    c.setEstado("AUTORIZADO");
                    return Mono.just(c);
                });
    }

    @Test
    @DisplayName("happy path: NC emitida y transmitida termina en AUTORIZADO, referencia persistida")
    void emitirNotaCredito_happyPath_autorizada() {
        Comprobante facturaOriginal = facturaAutorizadaCompania(1, 100L, new BigDecimal("50.00"));
        when(comprobanteRepository.findById(100L)).thenReturn(Mono.just(facturaOriginal));
        stubSaveNcWithId(300L);

        StepVerifier.create(service.emitirNotaCredito(buildCommand()))
                .assertNext(nc -> {
                    assertThat(nc.getEstado()).isEqualTo("AUTORIZADO");
                    assertThat(nc.getTipoComprobante()).isEqualTo("04");
                    assertThat(nc.getIdComprobanteRef()).isEqualTo(100L);
                    assertThat(nc.getSecuencial()).isEqualTo("000000042");
                    assertThat(nc.getClaveAcceso()).hasSize(49);
                    // La clave de acceso posición 9-10 debe ser "04"
                    assertThat(nc.getClaveAcceso().substring(8, 10)).isEqualTo("04");
                    // Total de la NC = valorModificacion del command
                    assertThat(nc.getTotal()).isEqualByComparingTo("30.00");
                    // Receptor copiado de la factura original
                    assertThat(nc.getIdReceptor()).isEqualTo("1712345678");
                    assertThat(nc.getRazonSocialReceptor()).isEqualTo("Cliente Original");
                })
                .verifyComplete();

        ArgumentCaptor<NotaCreditoReferencia> refCaptor = ArgumentCaptor.forClass(NotaCreditoReferencia.class);
        verify(notaCreditoReferenciaRepository).save(refCaptor.capture());
        NotaCreditoReferencia ref = refCaptor.getValue();
        assertThat(ref.getIdComprobante()).isEqualTo(300L);
        assertThat(ref.getCodDocModificado()).isEqualTo("01");
        assertThat(ref.getNumDocModificado()).isEqualTo("001-001-000000123");
        assertThat(ref.getIdMotivoAnulacion()).isEqualTo(1);
        assertThat(ref.getValorModificado()).isEqualByComparingTo("30.00");
        assertThat(ref.getRazon()).isEqualTo("Descuento cliente frecuente");
    }

    @Test
    @DisplayName("404 si la factura original no existe")
    void emitirNotaCredito_facturaOriginalInexistente_notFound() {
        when(comprobanteRepository.findById(100L)).thenReturn(Mono.empty());

        StepVerifier.create(service.emitirNotaCredito(buildCommand()))
                .expectError(NotFoundException.class)
                .verify();

        verify(secuencialRepository, never()).reservarSiguiente(anyInt(), anyInt(), anyString(), anyString(), anyString());
        verify(envioSriService, never()).procesarEmisionInmediataConXml(any(), anyString());
    }

    @Test
    @DisplayName("404 si la factura original pertenece a otra compañía (multi-tenant)")
    void emitirNotaCredito_facturaOriginalOtraCompania_notFound() {
        Comprobante otraCompania = facturaAutorizadaCompania(999, 100L, new BigDecimal("50.00"));
        when(comprobanteRepository.findById(100L)).thenReturn(Mono.just(otraCompania));

        StepVerifier.create(service.emitirNotaCredito(buildCommand()))
                .expectError(NotFoundException.class)
                .verify();

        verify(secuencialRepository, never()).reservarSiguiente(anyInt(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("422 si la factura original no está AUTORIZADO")
    void emitirNotaCredito_facturaOriginalNoAutorizada_business() {
        Comprobante generada = facturaAutorizadaCompania(1, 100L, new BigDecimal("50.00"));
        generada.setEstado("GENERADO");
        when(comprobanteRepository.findById(100L)).thenReturn(Mono.just(generada));

        StepVerifier.create(service.emitirNotaCredito(buildCommand()))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(BusinessException.class);
                    assertThat(e.getMessage()).contains("AUTORIZADO");
                })
                .verify();
    }

    @Test
    @DisplayName("422 si el comprobante referenciado no es una factura (tipo != 01)")
    void emitirNotaCredito_referenciaTipoInvalido_business() {
        Comprobante ncPrevia = facturaAutorizadaCompania(1, 100L, new BigDecimal("50.00"));
        ncPrevia.setTipoComprobante("04");
        when(comprobanteRepository.findById(100L)).thenReturn(Mono.just(ncPrevia));

        StepVerifier.create(service.emitirNotaCredito(buildCommand()))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(BusinessException.class);
                    assertThat(e.getMessage()).contains("tipo 01");
                })
                .verify();
    }

    @Test
    @DisplayName("propaga NotFoundException del catálogo si el motivo no existe")
    void emitirNotaCredito_motivoInvalido_notFound() {
        Comprobante facturaOriginal = facturaAutorizadaCompania(1, 100L, new BigDecimal("50.00"));
        when(comprobanteRepository.findById(100L)).thenReturn(Mono.just(facturaOriginal));
        when(catalogoSriService.obtenerMotivoAnulacion("DEVOLUCION"))
                .thenReturn(Mono.error(new NotFoundException("Motivo de anulación no reconocido: DEVOLUCION")));

        StepVerifier.create(service.emitirNotaCredito(buildCommand()))
                .expectError(NotFoundException.class)
                .verify();

        verify(secuencialRepository, never()).reservarSiguiente(anyInt(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("422 si valorModificacion excede el total de la factura original")
    void emitirNotaCredito_valorModificacionExcedeTotal_business() {
        Comprobante facturaOriginal = facturaAutorizadaCompania(1, 100L, new BigDecimal("20.00"));
        when(comprobanteRepository.findById(100L)).thenReturn(Mono.just(facturaOriginal));

        // El command trae valorModificacion=30.00, factura total=20.00
        StepVerifier.create(service.emitirNotaCredito(buildCommand()))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(BusinessException.class);
                    assertThat(e.getMessage()).contains("no puede exceder");
                })
                .verify();

        verify(secuencialRepository, never()).reservarSiguiente(anyInt(), anyInt(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("422 si valorModificacion es 0 o negativo")
    void emitirNotaCredito_valorModificacionNoPositivo_business() {
        Comprobante facturaOriginal = facturaAutorizadaCompania(1, 100L, new BigDecimal("50.00"));
        when(comprobanteRepository.findById(100L)).thenReturn(Mono.just(facturaOriginal));

        EmitirNotaCreditoCommand cmd = new EmitirNotaCreditoCommand(
                1, 1, LocalDate.of(2026, 7, 15),
                "001", "001", "123456789",
                100L, "DEVOLUCION", "Razon", BigDecimal.ZERO,
                List.of(new EmitirFacturaCommand.DetalleFacturaItem(
                        "PROD001", null, "x",
                        new BigDecimal("1.00"), new BigDecimal("0.00"), BigDecimal.ZERO,
                        BigDecimal.ZERO, null)),
                999);

        StepVerifier.create(service.emitirNotaCredito(cmd))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(BusinessException.class);
                    assertThat(e.getMessage()).contains("positivo");
                })
                .verify();
    }

    @Test
    @DisplayName("404 si no existe ConfigSri para la empresa")
    void emitirNotaCredito_sinConfigSri_notFound() {
        Comprobante facturaOriginal = facturaAutorizadaCompania(1, 100L, new BigDecimal("50.00"));
        when(comprobanteRepository.findById(100L)).thenReturn(Mono.just(facturaOriginal));
        when(configSriRepository.findByEmpresa(1, 1)).thenReturn(Mono.empty());

        StepVerifier.create(service.emitirNotaCredito(buildCommand()))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("devuelve el comprobante en estado ERROR cuando la transmisión inmediata falla (fallback resiliente)")
    void emitirNotaCredito_transmisionFalla_devuelveEstadoTransitorio() {
        Comprobante facturaOriginal = facturaAutorizadaCompania(1, 100L, new BigDecimal("50.00"));
        when(comprobanteRepository.findById(100L)).thenReturn(Mono.just(facturaOriginal));
        stubSaveNcWithId(400L);
        when(envioSriService.procesarEmisionInmediataConXml(any(Comprobante.class), anyString()))
                .thenAnswer(inv -> {
                    Comprobante c = inv.getArgument(0, Comprobante.class);
                    c.setEstado("ERROR");
                    return Mono.just(c);
                });

        StepVerifier.create(service.emitirNotaCredito(buildCommand()))
                .assertNext(nc -> assertThat(nc.getEstado()).isEqualTo("ERROR"))
                .verifyComplete();
    }

    @Test
    @DisplayName("buscarPorId: 404 si el comprobante existe pero es tipo 01 (no NC)")
    void buscarPorId_tipoFactura_notFound() {
        Comprobante factura = facturaAutorizadaCompania(1, 100L, new BigDecimal("50.00"));
        when(comprobanteRepository.findById(100L)).thenReturn(Mono.just(factura));

        StepVerifier.create(service.buscarPorId(100L, 1))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("buscarPorId: 404 si la NC pertenece a otra compañía")
    void buscarPorId_otraCompania_notFound() {
        Comprobante nc = Comprobante.builder()
                .id(300L).idCompania(999).tipoComprobante("04").build();
        when(comprobanteRepository.findById(300L)).thenReturn(Mono.just(nc));

        StepVerifier.create(service.buscarPorId(300L, 1))
                .expectError(NotFoundException.class)
                .verify();
    }

    // ---- helpers ----

    private void stubSaveNcWithId(long id) {
        when(comprobanteRepository.save(any(Comprobante.class))).thenAnswer(inv -> {
            Comprobante c = inv.getArgument(0, Comprobante.class);
            c.setId(id);
            return Mono.just(c);
        });
    }

    private ConfigSri defaultConfigSri() {
        return ConfigSri.builder()
                .idCompania(1).idSucursal(1)
                .ruc("1234567890001").ambiente("1")
                .razonSocial("Gym Test").build();
    }

    private Comprobante facturaAutorizadaCompania(Integer idCompania, Long id, BigDecimal total) {
        return Comprobante.builder()
                .id(id)
                .idCompania(idCompania)
                .idSucursal(1)
                .tipoComprobante("01")
                .estado("AUTORIZADO")
                .codEstablecimiento("001")
                .codPuntoEmision("001")
                .secuencial("000000123")
                .fechaEmision(LocalDate.of(2026, 6, 15))
                .tipoIdReceptor("05")
                .idReceptor("1712345678")
                .razonSocialReceptor("Cliente Original")
                .emailReceptor("cliente@test.local")
                .total(total)
                .build();
    }

    private EmitirNotaCreditoCommand buildCommand() {
        return new EmitirNotaCreditoCommand(
                1, 1,
                LocalDate.of(2026, 7, 15),
                "001", "001", "123456789",
                100L,
                "DEVOLUCION",
                "Descuento cliente frecuente",
                new BigDecimal("30.00"),
                List.of(new EmitirFacturaCommand.DetalleFacturaItem(
                        "PROD001", null, "Membresía mensual",
                        new BigDecimal("1.00"), new BigDecimal("30.00"), BigDecimal.ZERO,
                        new BigDecimal("30.00"), null)),
                999);
    }
}
