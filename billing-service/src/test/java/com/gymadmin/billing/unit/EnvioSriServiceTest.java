package com.gymadmin.billing.unit;

import com.gymadmin.billing.application.service.EnvioSriService;
import com.gymadmin.billing.domain.model.ColaEnvio;
import com.gymadmin.billing.domain.model.Comprobante;
import com.gymadmin.billing.domain.model.ComprobanteDetalle;
import com.gymadmin.billing.domain.model.ConfigSri;
import com.gymadmin.billing.domain.model.EnvioSri;
import com.gymadmin.billing.domain.model.sri.RespuestaAutorizacion;
import com.gymadmin.billing.domain.model.sri.RespuestaRecepcion;
import com.gymadmin.billing.domain.port.out.CertificadoRepository;
import com.gymadmin.billing.domain.port.out.ColaEnvioRepository;
import com.gymadmin.billing.domain.port.out.ComprobanteRepository;
import com.gymadmin.billing.domain.port.out.ConfigSriRepository;
import com.gymadmin.billing.domain.port.out.EmailNotificationPort;
import com.gymadmin.billing.domain.port.out.EnvioSriRepository;
import com.gymadmin.billing.domain.port.out.FileStoragePort;
import com.gymadmin.billing.domain.port.out.RidePdfPort;
import com.gymadmin.billing.domain.port.out.SriSoapPort;
import com.gymadmin.billing.domain.port.out.XmlSignaturePort;
import com.gymadmin.billing.infrastructure.adapter.out.xml.FacturaXmlBuilder;
import com.gymadmin.billing.infrastructure.config.SriTimeoutProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("EnvioSriService.procesarEmisionInmediata — G2 transmisión inmediata al SRI")
class EnvioSriServiceTest {

    private ComprobanteRepository comprobanteRepository;
    private EnvioSriRepository envioSriRepository;
    private ColaEnvioRepository colaEnvioRepository;
    private SriSoapPort sriSoapPort;
    private CertificadoRepository certificadoRepository;
    private XmlSignaturePort xmlSignaturePort;
    private FacturaXmlBuilder facturaXmlBuilder;
    private FileStoragePort fileStoragePort;
    private RidePdfPort ridePdfPort;
    private EmailNotificationPort emailNotificationPort;
    private ConfigSriRepository configSriRepository;
    private SriTimeoutProperties sriTimeoutProperties;

    private SimpleMeterRegistry meterRegistry;

    private EnvioSriService service;

    @BeforeEach
    void setUp() {
        comprobanteRepository = mock(ComprobanteRepository.class);
        envioSriRepository = mock(EnvioSriRepository.class);
        colaEnvioRepository = mock(ColaEnvioRepository.class);
        sriSoapPort = mock(SriSoapPort.class);
        certificadoRepository = mock(CertificadoRepository.class);
        xmlSignaturePort = mock(XmlSignaturePort.class);
        facturaXmlBuilder = mock(FacturaXmlBuilder.class);
        fileStoragePort = mock(FileStoragePort.class);
        ridePdfPort = mock(RidePdfPort.class);
        emailNotificationPort = mock(EmailNotificationPort.class);
        configSriRepository = mock(ConfigSriRepository.class);

        sriTimeoutProperties = new SriTimeoutProperties();
        sriTimeoutProperties.setEnvioSeconds(2); // timeout corto para tests

        meterRegistry = new SimpleMeterRegistry();
        Counter emitidos = Counter.builder("test.emitidos").register(meterRegistry);
        Counter emitidosNc = Counter.builder("test.emitidos_nc").register(meterRegistry);
        Counter autorizados = Counter.builder("test.autorizados").register(meterRegistry);
        Counter erroresSri = Counter.builder("test.errores").register(meterRegistry);
        Counter reintentos = Counter.builder("test.reintentos").register(meterRegistry);
        Timer duracion = Timer.builder("test.duracion").register(meterRegistry);
        Counter timeouts = Counter.builder("test.timeouts").register(meterRegistry);

        // Stubs generales
        when(certificadoRepository.getActiveCertificateContent(anyInt(), anyInt()))
                .thenReturn(Mono.just(new byte[]{1, 2, 3}));
        when(certificadoRepository.getActiveCertificatePassword(anyInt(), anyInt()))
                .thenReturn(Mono.just("password"));
        when(xmlSignaturePort.sign(anyString(), any(byte[].class), anyString()))
                .thenReturn(Mono.just("<signed>xml</signed>"));
        when(facturaXmlBuilder.buildXml(any(), any(), any(), any(), any()))
                .thenReturn("<factura>xml</factura>");
        when(comprobanteRepository.updateEstado(anyLong(), anyString(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> {
                    Comprobante c = baseComprobante();
                    c.setEstado(inv.getArgument(1));
                    c.setNumeroAutorizacion(inv.getArgument(6));
                    c.setFechaAutorizacion(inv.getArgument(5));
                    return Mono.just(c);
                });
        when(envioSriRepository.save(any(EnvioSri.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0, EnvioSri.class)));
        when(colaEnvioRepository.save(any(ColaEnvio.class)))
                .thenAnswer(inv -> {
                    ColaEnvio c = inv.getArgument(0, ColaEnvio.class);
                    c.setId(999L);
                    return Mono.just(c);
                });
        when(colaEnvioRepository.update(any(ColaEnvio.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0, ColaEnvio.class)));
        // Detalles vacíos para el path de RIDE (no interesa aquí)
        when(comprobanteRepository.findDetallesByIdComprobante(anyLong()))
                .thenReturn(reactor.core.publisher.Flux.empty());
        when(configSriRepository.findByEmpresa(anyInt(), anyInt()))
                .thenReturn(Mono.just(defaultConfigSri()));
        when(ridePdfPort.generarRide(any(), any(), any()))
                .thenReturn(Mono.just(new byte[]{9, 8, 7}));
        when(fileStoragePort.saveRidePdf(anyLong(), any(byte[].class)))
                .thenReturn(Mono.just("blob://ride.pdf"));
        when(emailNotificationPort.enviarFactura(any(), any())).thenReturn(Mono.empty());

        service = new EnvioSriService(
                comprobanteRepository,
                envioSriRepository,
                colaEnvioRepository,
                sriSoapPort,
                certificadoRepository,
                xmlSignaturePort,
                facturaXmlBuilder,
                fileStoragePort,
                ridePdfPort,
                emailNotificationPort,
                configSriRepository,
                sriTimeoutProperties,
                emitidos, emitidosNc, autorizados, erroresSri, reintentos, duracion, timeouts
        );
    }

    @Test
    @DisplayName("AUTORIZADO · devuelve el comprobante en AUTORIZADO y NO crea fila en cola_envio")
    void procesarEmisionInmediata_autorizado_noEncola() {
        when(sriSoapPort.enviarComprobante(anyString(), anyString()))
                .thenReturn(Mono.just(RespuestaRecepcion.builder().estado("RECIBIDA").build()));
        when(sriSoapPort.autorizarComprobante(anyString(), anyString()))
                .thenReturn(Mono.just(RespuestaAutorizacion.builder()
                        .estado("AUTORIZADO")
                        .numeroAutorizacion("AUT-123")
                        .fechaAutorizacion(OffsetDateTime.now())
                        .build()));

        StepVerifier.create(service.procesarEmisionInmediata(baseComprobante(), List.of(), defaultPagos(), defaultConfigSri()))
                .assertNext(c -> assertThat(c.getEstado()).isEqualTo("AUTORIZADO"))
                .verifyComplete();

        verify(colaEnvioRepository, never()).save(any(ColaEnvio.class));
        verify(colaEnvioRepository, never()).update(any(ColaEnvio.class));
    }

    @Test
    @DisplayName("DEVUELTO · deja el comprobante en DEVUELTO y encola en cola_envio con backoff (proxima_ejecucion > now)")
    void procesarEmisionInmediata_devuelto_encolaConBackoff() {
        when(sriSoapPort.enviarComprobante(anyString(), anyString()))
                .thenReturn(Mono.just(RespuestaRecepcion.builder()
                        .estado("DEVUELTA")
                        .mensajes(List.of("Firma inválida")).build()));

        StepVerifier.create(service.procesarEmisionInmediata(baseComprobante(), List.of(), defaultPagos(), defaultConfigSri()))
                .assertNext(c -> assertThat(c.getEstado()).isEqualTo("DEVUELTO"))
                .verifyComplete();

        ArgumentCaptor<ColaEnvio> captor = ArgumentCaptor.forClass(ColaEnvio.class);
        verify(colaEnvioRepository).save(captor.capture());
        ColaEnvio saved = captor.getValue();
        assertThat(saved.getEstado()).isEqualTo("PENDIENTE");
        assertThat(saved.getIntentos()).isEqualTo((short) 1);
        // Backoff: al menos 30 s en el futuro (delay real = 1 min)
        assertThat(saved.getProximaEjecucion()).isAfter(OffsetDateTime.now().plusSeconds(30));
    }

    @Test
    @DisplayName("Timeout · deja el comprobante en ERROR y encola con proxima_ejecucion=now(); incrementa contador de timeouts")
    void procesarEmisionInmediata_timeout_encolaInmediato() {
        // El adapter tarda 20s → excede timeout de 2s
        when(sriSoapPort.enviarComprobante(anyString(), anyString()))
                .thenReturn(Mono.delay(Duration.ofSeconds(20))
                        .then(Mono.just(RespuestaRecepcion.builder().estado("RECIBIDA").build())));

        StepVerifier.create(service.procesarEmisionInmediata(baseComprobante(), List.of(), defaultPagos(), defaultConfigSri()))
                .assertNext(c -> assertThat(c.getEstado()).isEqualTo("ERROR"))
                .verifyComplete();

        ArgumentCaptor<ColaEnvio> captor = ArgumentCaptor.forClass(ColaEnvio.class);
        verify(colaEnvioRepository).save(captor.capture());
        ColaEnvio saved = captor.getValue();
        assertThat(saved.getEstado()).isEqualTo("PENDIENTE");
        // proxima_ejecucion ≈ now() (dentro de 5 s)
        assertThat(saved.getProximaEjecucion()).isBefore(OffsetDateTime.now().plusSeconds(5));
        assertThat(saved.getUltimoError()).contains("Timeout");
        assertThat(meterRegistry.get("test.timeouts").counter().count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Error inesperado · deja el comprobante en ERROR y encola con proxima_ejecucion=now()")
    void procesarEmisionInmediata_errorInesperado_encolaInmediato() {
        when(sriSoapPort.enviarComprobante(anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("connection reset by peer")));

        StepVerifier.create(service.procesarEmisionInmediata(baseComprobante(), List.of(), defaultPagos(), defaultConfigSri()))
                .assertNext(c -> assertThat(c.getEstado()).isEqualTo("ERROR"))
                .verifyComplete();

        ArgumentCaptor<ColaEnvio> captor = ArgumentCaptor.forClass(ColaEnvio.class);
        verify(colaEnvioRepository).save(captor.capture());
        ColaEnvio saved = captor.getValue();
        assertThat(saved.getEstado()).isEqualTo("PENDIENTE");
        assertThat(saved.getProximaEjecucion()).isBefore(OffsetDateTime.now().plusSeconds(5));
        assertThat(saved.getUltimoError()).contains("connection reset");
        // No es TimeoutException → el counter de timeouts NO se incrementa
        assertThat(meterRegistry.get("test.timeouts").counter().count()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Construye el XML real usando FacturaXmlBuilder (elimina el placeholder G2)")
    void procesarEmisionInmediata_llamaAlBuilderConDetallesYPagos() {
        when(sriSoapPort.enviarComprobante(anyString(), anyString()))
                .thenReturn(Mono.just(RespuestaRecepcion.builder().estado("RECIBIDA").build()));
        when(sriSoapPort.autorizarComprobante(anyString(), anyString()))
                .thenReturn(Mono.just(RespuestaAutorizacion.builder()
                        .estado("AUTORIZADO")
                        .numeroAutorizacion("AUT-123")
                        .fechaAutorizacion(OffsetDateTime.now())
                        .build()));

        List<ComprobanteDetalle> detalles = List.of(ComprobanteDetalle.builder()
                .codigoPrincipal("PROD001")
                .descripcion("Membresía")
                .cantidad(new BigDecimal("1"))
                .precioUnitario(new BigDecimal("50.00"))
                .descuento(BigDecimal.ZERO)
                .precioTotalSinImpuesto(new BigDecimal("50.00"))
                .build());
        List<FacturaXmlBuilder.Pago> pagos = List.of(new FacturaXmlBuilder.Pago("01", new BigDecimal("57.50")));
        ConfigSri config = defaultConfigSri();

        StepVerifier.create(service.procesarEmisionInmediata(baseComprobante(), detalles, pagos, config))
                .assertNext(c -> assertThat(c.getEstado()).isEqualTo("AUTORIZADO"))
                .verifyComplete();

        verify(facturaXmlBuilder).buildXml(any(Comprobante.class), eq(detalles), eq(config), any(), eq(pagos));
    }

    private Comprobante baseComprobante() {
        return Comprobante.builder()
                .id(500L)
                .idCompania(1)
                .idSucursal(1)
                .tipoComprobante("01")
                .claveAcceso("2607202601123456789000110010010000000010000000010")
                .codEstablecimiento("001")
                .codPuntoEmision("001")
                .secuencial("000000001")
                .fechaEmision(LocalDate.of(2026, 7, 26))
                .ambiente("1")
                .tipoIdReceptor("05")
                .idReceptor("1712345678")
                .razonSocialReceptor("Cliente Test")
                .subtotalSinImpuesto(new BigDecimal("50.00"))
                .totalIva(new BigDecimal("7.50"))
                .totalDescuento(BigDecimal.ZERO)
                .total(new BigDecimal("57.50"))
                .moneda("DOLAR")
                .estado("GENERADO")
                .build();
    }

    private ConfigSri defaultConfigSri() {
        return ConfigSri.builder()
                .idCompania(1)
                .idSucursal(1)
                .ruc("1234567890001")
                .razonSocial("Gimnasio Test")
                .ambiente("1")
                .obligadoContabilidad(false)
                .dirEstablecimiento("Av. Test 123")
                .build();
    }

    private List<FacturaXmlBuilder.Pago> defaultPagos() {
        return List.of(new FacturaXmlBuilder.Pago("01", new BigDecimal("57.50")));
    }
}
