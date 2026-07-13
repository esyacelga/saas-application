package com.gymadmin.billing.unit;

import com.gymadmin.billing.application.command.EmitirFacturaCommand;
import com.gymadmin.billing.application.service.CatalogoSriService;
import com.gymadmin.billing.application.service.ComprobanteService;
import com.gymadmin.billing.application.service.EnvioSriService;
import com.gymadmin.billing.domain.model.Comprobante;
import com.gymadmin.billing.domain.model.ConfigSri;
import com.gymadmin.billing.domain.port.out.CertificadoRepository;
import com.gymadmin.billing.domain.port.out.ComprobanteRepository;
import com.gymadmin.billing.domain.port.out.ConfigSriRepository;
import com.gymadmin.billing.domain.port.out.EmailNotificationPort;
import com.gymadmin.billing.domain.port.out.FileStoragePort;
import com.gymadmin.billing.domain.port.out.SecuencialRepository;
import com.gymadmin.billing.domain.port.out.XmlSignaturePort;
import com.gymadmin.billing.infrastructure.adapter.out.xml.FacturaXmlBuilder;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ComprobanteService.emitirFactura — reserva atómica del secuencial (G5)")
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
        when(comprobanteRepository.save(any(Comprobante.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0, Comprobante.class)));

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
        assertThat(saved.getEstado()).isEqualTo("GENERADO");
        // La clave de acceso (49 dígitos) debe contener el secuencial formateado
        assertThat(saved.getClaveAcceso()).hasSize(49);
        assertThat(saved.getClaveAcceso()).contains("000000042");
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
        when(comprobanteRepository.save(any(Comprobante.class)))
                .thenAnswer(inv -> Mono.just(inv.getArgument(0, Comprobante.class)));

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
        verify(secuencialRepository, org.mockito.Mockito.never())
                .reservarSiguiente(anyInt(), anyInt(), anyString(), anyString(), eq("01"));
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
}
