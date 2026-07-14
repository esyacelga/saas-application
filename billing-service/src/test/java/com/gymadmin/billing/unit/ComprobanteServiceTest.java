package com.gymadmin.billing.unit;

import com.gymadmin.billing.application.command.EmitirFacturaCommand;
import com.gymadmin.billing.application.service.CatalogoSriService;
import com.gymadmin.billing.application.service.ComprobanteService;
import com.gymadmin.billing.application.service.EnvioSriService;
import com.gymadmin.billing.domain.model.Comprobante;
import com.gymadmin.billing.domain.model.ComprobanteDetalle;
import com.gymadmin.billing.domain.model.ConfigSri;
import com.gymadmin.billing.domain.port.out.CertificadoRepository;
import com.gymadmin.billing.domain.port.out.ComprobanteRepository;
import com.gymadmin.billing.domain.port.out.ConfigSriRepository;
import com.gymadmin.billing.domain.port.out.EmailNotificationPort;
import com.gymadmin.billing.domain.port.out.FileStoragePort;
import com.gymadmin.billing.domain.port.out.SecuencialRepository;
import com.gymadmin.billing.domain.port.out.XmlSignaturePort;
import com.gymadmin.billing.infrastructure.adapter.out.xml.FacturaXmlBuilder;
import com.gymadmin.billing.infrastructure.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ComprobanteService.emitirFactura — reserva secuencial (G5) + transmisión inmediata (G2)")
class ComprobanteServiceTest {

    private ComprobanteRepository comprobanteRepository;
    private ConfigSriRepository configSriRepository;
    private CertificadoRepository certificadoRepository;
    private XmlSignaturePort xmlSignaturePort;
    private FacturaXmlBuilder facturaXmlBuilder;
    private EnvioSriService envioSriService;
    private FileStoragePort fileStoragePort;
    private EmailNotificationPort emailNotificationPort;
    private SecuencialRepository secuencialRepository;
    private CatalogoSriService catalogoSriService;

    private ComprobanteService service;

    @BeforeEach
    void setUp() {
        comprobanteRepository = mock(ComprobanteRepository.class);
        configSriRepository = mock(ConfigSriRepository.class);
        certificadoRepository = mock(CertificadoRepository.class);
        xmlSignaturePort = mock(XmlSignaturePort.class);
        facturaXmlBuilder = mock(FacturaXmlBuilder.class);
        envioSriService = mock(EnvioSriService.class);
        fileStoragePort = mock(FileStoragePort.class);
        emailNotificationPort = mock(EmailNotificationPort.class);
        secuencialRepository = mock(SecuencialRepository.class);
        catalogoSriService = mock(CatalogoSriService.class);

        // Por defecto los catálogos SRI validan OK; los tests que necesiten
        // otro comportamiento pueden reemplazar estos stubs.
        when(catalogoSriService.existeTipoIdentificacion(anyString())).thenReturn(Mono.just(true));
        when(catalogoSriService.existeFormaPago(anyString())).thenReturn(Mono.just(true));
        // G10: por defecto ninguna forma de pago es bancarizada. Solo importa en los
        // tests de bancarización, que stubean los códigos 16-20 explícitamente; el
        // resto emite por debajo del umbral de USD 500 y nunca consulta este flag.
        when(catalogoSriService.esBancarizada(anyString())).thenReturn(Mono.just(false));

        // Default para G2: la transmisión inmediata devuelve el comprobante ya
        // AUTORIZADO. Los tests que necesitan otro camino sobrescriben este stub.
        when(envioSriService.procesarEmisionInmediata(any(Comprobante.class), anyList(), anyList(), any(ConfigSri.class)))
                .thenAnswer(inv -> {
                    Comprobante c = inv.getArgument(0, Comprobante.class);
                    c.setEstado("AUTORIZADO");
                    return Mono.just(c);
                });

        service = new ComprobanteService(
                comprobanteRepository,
                configSriRepository,
                certificadoRepository,
                xmlSignaturePort,
                facturaXmlBuilder,
                envioSriService,
                fileStoragePort,
                emailNotificationPort,
                secuencialRepository,
                catalogoSriService
        );
    }

    @Test
    @DisplayName("usa el secuencial devuelto por el repositorio, formateado a 9 dígitos con padding")
    void emitirFactura_usaSecuencialDelRepositorio_conPaddingA9Digitos() {
        // Arrange
        ConfigSri config = ConfigSri.builder()
                .idCompania(1)
                .idSucursal(1)
                .ruc("1234567890001")
                .ambiente("1")
                .build();

        when(configSriRepository.findByEmpresa(1, 1)).thenReturn(Mono.just(config));
        when(secuencialRepository.reservarSiguiente(1, 1, "001", "001", "01"))
                .thenReturn(Mono.just(42));
        // Capturamos el estado inicial (GENERADO) antes de que la transmisión
        // inmediata (G2) mute el objeto a AUTORIZADO/ERROR/etc.
        AtomicReferenceEstado estadoInicial = new AtomicReferenceEstado();
        when(comprobanteRepository.save(any(Comprobante.class))).thenAnswer(inv -> {
            Comprobante c = inv.getArgument(0, Comprobante.class);
            c.setId(100L);
            estadoInicial.value = c.getEstado();
            return Mono.just(c);
        });

        EmitirFacturaCommand command = buildCommand();

        // Act
        Mono<Comprobante> result = service.emitirFactura(command);

        // Assert
        StepVerifier.create(result)
                .assertNext(c -> assertThat(c.getSecuencial()).isEqualTo("000000042"))
                .verifyComplete();

        ArgumentCaptor<Comprobante> captor = ArgumentCaptor.forClass(Comprobante.class);
        verify(comprobanteRepository).save(captor.capture());
        Comprobante saved = captor.getValue();
        assertThat(saved.getSecuencial()).isEqualTo("000000042");
        assertThat(saved.getTipoComprobante()).isEqualTo("01");
        // El comprobante se persiste inicialmente como GENERADO (antes de G2)
        assertThat(estadoInicial.value).isEqualTo("GENERADO");
        // La clave de acceso (49 dígitos) debe contener el secuencial formateado
        assertThat(saved.getClaveAcceso()).hasSize(49);
        assertThat(saved.getClaveAcceso()).contains("000000042");
    }

    /** Contenedor no-atómico, solo para escribir desde un lambda de Mockito. */
    private static final class AtomicReferenceEstado {
        String value;
    }

    @Test
    @DisplayName("reserva el secuencial pasando el tipo de comprobante '01' (factura)")
    void emitirFactura_reservaSecuencial_conTipoComprobante01() {
        // Arrange
        ConfigSri config = ConfigSri.builder()
                .idCompania(1)
                .idSucursal(1)
                .ruc("1234567890001")
                .ambiente("1")
                .build();

        when(configSriRepository.findByEmpresa(anyInt(), anyInt())).thenReturn(Mono.just(config));
        when(secuencialRepository.reservarSiguiente(anyInt(), anyInt(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(1));
        stubSaveWithId(comprobanteRepository, 101L);

        // Act
        StepVerifier.create(service.emitirFactura(buildCommand())).expectNextCount(1).verifyComplete();

        // Assert
        verify(secuencialRepository).reservarSiguiente(1, 1, "001", "001", "01");
    }

    @Test
    @DisplayName("propaga NotFoundException cuando no existe ConfigSri para la empresa")
    void emitirFactura_sinConfigSri_falla() {
        when(configSriRepository.findByEmpresa(anyInt(), anyInt())).thenReturn(Mono.empty());

        StepVerifier.create(service.emitirFactura(buildCommand()))
                .expectErrorMatches(err -> err.getClass().getSimpleName().equals("NotFoundException"))
                .verify();

        // No debe reservar secuencial si no hay config
        verify(secuencialRepository, never())
                .reservarSiguiente(anyInt(), anyInt(), anyString(), anyString(), eq("01"));
        // Ni intentar transmitir
        verify(envioSriService, never())
                .procesarEmisionInmediata(any(), any(), any(), any());
    }

    @Test
    @DisplayName("G2 · dispara la transmisión inmediata pasando detalles y pagos del command (sin releer BD)")
    void emitirFactura_disparaTransmisionInmediata_conDatosDelCommand() {
        ConfigSri config = ConfigSri.builder().idCompania(1).idSucursal(1).ruc("1234567890001").ambiente("1").build();
        when(configSriRepository.findByEmpresa(anyInt(), anyInt())).thenReturn(Mono.just(config));
        when(secuencialRepository.reservarSiguiente(anyInt(), anyInt(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(7));
        stubSaveWithId(comprobanteRepository, 200L);

        StepVerifier.create(service.emitirFactura(buildCommand())).expectNextCount(1).verifyComplete();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ComprobanteDetalle>> detallesCaptor = ArgumentCaptor.forClass((Class) List.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FacturaXmlBuilder.Pago>> pagosCaptor = ArgumentCaptor.forClass((Class) List.class);

        verify(envioSriService, times(1)).procesarEmisionInmediata(
                any(Comprobante.class),
                detallesCaptor.capture(),
                pagosCaptor.capture(),
                eq(config));

        assertThat(detallesCaptor.getValue())
                .hasSize(1)
                .allSatisfy(d -> {
                    assertThat(d.getCodigoPrincipal()).isEqualTo("PROD001");
                    assertThat(d.getDescripcion()).isEqualTo("Membresía mensual");
                    assertThat(d.getPrecioTotalSinImpuesto()).isEqualByComparingTo("50.00");
                    // El detalle apunta al ID del comprobante recién persistido
                    assertThat(d.getIdComprobante()).isEqualTo(200L);
                });

        assertThat(pagosCaptor.getValue())
                .hasSize(1)
                .allSatisfy(p -> {
                    assertThat(p.formaPago()).isEqualTo("01");
                    assertThat(p.total()).isEqualByComparingTo("50.00");
                });
    }

    @Test
    @DisplayName("G2 · devuelve el comprobante en estado AUTORIZADO cuando la transmisión inmediata tiene éxito")
    void emitirFactura_transmisionInmediataAutorizada_devuelveAutorizado() {
        ConfigSri config = ConfigSri.builder().idCompania(1).idSucursal(1).ruc("1234567890001").ambiente("1").build();
        when(configSriRepository.findByEmpresa(anyInt(), anyInt())).thenReturn(Mono.just(config));
        when(secuencialRepository.reservarSiguiente(anyInt(), anyInt(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(1));
        stubSaveWithId(comprobanteRepository, 300L);
        // El stub por defecto ya devuelve AUTORIZADO

        StepVerifier.create(service.emitirFactura(buildCommand()))
                .assertNext(c -> assertThat(c.getEstado()).isEqualTo("AUTORIZADO"))
                .verifyComplete();
    }

    @Test
    @DisplayName("G2 · devuelve el comprobante en estado ERROR cuando la transmisión inmediata falla (fallback resiliente)")
    void emitirFactura_transmisionInmediataFalla_devuelveEstadoTransitorio() {
        ConfigSri config = ConfigSri.builder().idCompania(1).idSucursal(1).ruc("1234567890001").ambiente("1").build();
        when(configSriRepository.findByEmpresa(anyInt(), anyInt())).thenReturn(Mono.just(config));
        when(secuencialRepository.reservarSiguiente(anyInt(), anyInt(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(1));
        stubSaveWithId(comprobanteRepository, 400L);

        // Emisión inmediata: el pipeline síncrono deja el comprobante en ERROR
        when(envioSriService.procesarEmisionInmediata(any(Comprobante.class), anyList(), anyList(), any(ConfigSri.class)))
                .thenAnswer(inv -> {
                    Comprobante c = inv.getArgument(0, Comprobante.class);
                    c.setEstado("ERROR");
                    return Mono.just(c);
                });

        StepVerifier.create(service.emitirFactura(buildCommand()))
                .assertNext(c -> assertThat(c.getEstado()).isEqualTo("ERROR"))
                .verifyComplete();
    }

    private static void stubSaveWithId(ComprobanteRepository repo, long id) {
        AtomicLong assigned = new AtomicLong(id);
        when(repo.save(any(Comprobante.class))).thenAnswer(inv -> {
            Comprobante c = inv.getArgument(0, Comprobante.class);
            c.setId(assigned.get());
            return Mono.just(c);
        });
    }

    private EmitirFacturaCommand buildCommand() {
        return new EmitirFacturaCommand(
                1,
                1,
                LocalDate.of(2026, 7, 11),
                "001",
                "001",
                "123456789",
                "05",
                "1712345678",
                "Cliente Test",
                "cliente@test.local",
                "Av. Test 123",
                "0999999999",
                List.of(new EmitirFacturaCommand.DetalleFacturaItem(
                        "PROD001",
                        null,
                        "Membresía mensual",
                        new BigDecimal("1.00"),
                        new BigDecimal("50.00"),
                        BigDecimal.ZERO,
                        new BigDecimal("50.00"),
                        null
                )),
                List.of(new EmitirFacturaCommand.PagoItem("01", new BigDecimal("50.00"))),
                "01",
                null,
                null,
                999
        );
    }

    /**
     * Igual que {@link #buildCommand()} pero con los pagos indicados. El total del
     * comprobante es la suma de los pagos, así que esto controla el monto emitido.
     */
    private EmitirFacturaCommand buildCommandConPagos(EmitirFacturaCommand.PagoItem... pagos) {
        EmitirFacturaCommand base = buildCommand();
        return new EmitirFacturaCommand(
                base.idCompania(),
                base.idSucursal(),
                base.fechaEmision(),
                base.codEstablecimiento(),
                base.codPuntoEmision(),
                base.codigoNumerico(),
                base.tipoIdReceptor(),
                base.idReceptor(),
                base.razonSocialReceptor(),
                base.emailReceptor(),
                base.direccionReceptor(),
                base.telefonoReceptor(),
                base.detalles(),
                List.of(pagos),
                base.formaPago(),
                base.idMembresia(),
                base.idVenta(),
                base.idUsuarioRegistro()
        );
    }

    /** Stubs mínimos para que emitirFactura llegue hasta la validación de catálogos. */
    private void stubEmisionOk() {
        ConfigSri config = ConfigSri.builder()
                .idCompania(1).idSucursal(1).ruc("1234567890001").ambiente("1").build();
        when(configSriRepository.findByEmpresa(1, 1)).thenReturn(Mono.just(config));
        when(secuencialRepository.reservarSiguiente(1, 1, "001", "001", "01"))
                .thenReturn(Mono.just(42));
        when(comprobanteRepository.save(any(Comprobante.class))).thenAnswer(inv -> {
            Comprobante c = inv.getArgument(0, Comprobante.class);
            c.setId(100L);
            return Mono.just(c);
        });
    }

    // ─── G10 · Bancarización sobre USD 500 ───────────────────────────────────────

    @Test
    @DisplayName("G10: total ≤ 500 en efectivo emite normalmente (la regla no aplica)")
    void bancarizacion_bajoUmbral_noAplica() {
        stubEmisionOk();
        // Exactamente el umbral: la norma exige bancarizar lo que lo *supera*.
        EmitirFacturaCommand command = buildCommandConPagos(
                new EmitirFacturaCommand.PagoItem("01", new BigDecimal("500.00")));

        StepVerifier.create(service.emitirFactura(command))
                .assertNext(c -> assertThat(c.getId()).isEqualTo(100L))
                .verifyComplete();
    }

    @Test
    @DisplayName("G10: total > 500 pagado 100% en efectivo es rechazado")
    void bancarizacion_sobreUmbralSinBancarizar_falla() {
        stubEmisionOk();
        EmitirFacturaCommand command = buildCommandConPagos(
                new EmitirFacturaCommand.PagoItem("01", new BigDecimal("600.00")));

        StepVerifier.create(service.emitirFactura(command))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(BusinessException.class);
                    assertThat(e.getMessage()).contains("bancarizada");
                })
                .verify();

        verify(comprobanteRepository, never()).save(any(Comprobante.class));
    }

    @Test
    @DisplayName("G10: pago mixto que cubre el excedente con tarjeta es aceptado")
    void bancarizacion_pagoMixtoQueCubreExcedente_ok() {
        stubEmisionOk();
        when(catalogoSriService.esBancarizada("19")).thenReturn(Mono.just(true));

        // Total 600 → excedente 100. La tarjeta aporta 500 ≥ 100.
        EmitirFacturaCommand command = buildCommandConPagos(
                new EmitirFacturaCommand.PagoItem("01", new BigDecimal("100.00")),
                new EmitirFacturaCommand.PagoItem("19", new BigDecimal("500.00")));

        StepVerifier.create(service.emitirFactura(command))
                .assertNext(c -> assertThat(c.getId()).isEqualTo(100L))
                .verifyComplete();
    }

    @Test
    @DisplayName("G10: pago mixto cuyo tramo bancarizado no cubre el excedente es rechazado")
    void bancarizacion_pagoMixtoInsuficiente_falla() {
        stubEmisionOk();
        when(catalogoSriService.esBancarizada("19")).thenReturn(Mono.just(true));

        // Total 600 → excedente 100, pero solo 50 van por tarjeta.
        EmitirFacturaCommand command = buildCommandConPagos(
                new EmitirFacturaCommand.PagoItem("01", new BigDecimal("550.00")),
                new EmitirFacturaCommand.PagoItem("19", new BigDecimal("50.00")));

        StepVerifier.create(service.emitirFactura(command))
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(BusinessException.class);
                    assertThat(e.getMessage()).contains("100.00");
                })
                .verify();

        verify(comprobanteRepository, never()).save(any(Comprobante.class));
    }

    @Test
    @DisplayName("G10: una forma no bancarizada (21 endoso) no cuenta para cubrir el excedente")
    void bancarizacion_formaNoBancarizada_noCuenta() {
        stubEmisionOk();
        // 21 ENDOSO_TITULOS quedó marcado como no bancarizado en el catálogo.
        when(catalogoSriService.esBancarizada("21")).thenReturn(Mono.just(false));

        EmitirFacturaCommand command = buildCommandConPagos(
                new EmitirFacturaCommand.PagoItem("21", new BigDecimal("600.00")));

        StepVerifier.create(service.emitirFactura(command))
                .expectError(BusinessException.class)
                .verify();
    }
}
