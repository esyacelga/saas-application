package com.gymadmin.billing.unit;

import com.gymadmin.billing.application.service.CatalogoSriService;
import com.gymadmin.billing.domain.model.sri.FormaPagoSri;
import com.gymadmin.billing.domain.model.sri.MotivoAnulacionNcSri;
import com.gymadmin.billing.domain.model.sri.TarifaIvaSri;
import com.gymadmin.billing.domain.model.sri.TipoComprobanteSri;
import com.gymadmin.billing.domain.model.sri.TipoIdentificacionSri;
import com.gymadmin.billing.domain.port.out.CatalogoSriRepository;
import com.gymadmin.billing.infrastructure.exception.BusinessException;
import com.gymadmin.billing.infrastructure.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CatalogoSriService — lookups cacheados y validación de vigencia (G6)")
class CatalogoSriServiceTest {

    private CatalogoSriRepository repository;
    private CatalogoSriService service;

    @BeforeEach
    void setUp() {
        repository = mock(CatalogoSriRepository.class);
        service = new CatalogoSriService(repository);
    }

    @Test
    @DisplayName("obtenerTipoComprobante — dos llamadas seguidas invocan el repo solo una vez")
    void obtenerTipoComprobante_dosLlamadas_repositorioInvocadoUnaVez() {
        TipoComprobanteSri factura = new TipoComprobanteSri("01", "FACTURA", "2.1.0", true);
        when(repository.findTipoComprobante("01")).thenReturn(Mono.just(factura));

        StepVerifier.create(service.obtenerTipoComprobante("01"))
                .expectNext(factura)
                .verifyComplete();
        StepVerifier.create(service.obtenerTipoComprobante("01"))
                .expectNext(factura)
                .verifyComplete();

        verify(repository, times(1)).findTipoComprobante("01");
    }

    @Test
    @DisplayName("obtenerTipoComprobante — código inexistente propaga NotFoundException")
    void obtenerTipoComprobante_codigoNoExiste_lanzaNotFound() {
        when(repository.findTipoComprobante("99")).thenReturn(Mono.empty());

        StepVerifier.create(service.obtenerTipoComprobante("99"))
                .expectErrorSatisfies(err -> assertThat(err)
                        .isInstanceOf(NotFoundException.class)
                        .hasMessageContaining("99"))
                .verify();
    }

    @Test
    @DisplayName("existeFormaPago — devuelve true si existe, false si no")
    void existeFormaPago_delegaEnHasElement() {
        when(repository.findFormaPago("01"))
                .thenReturn(Mono.just(new FormaPagoSri("01", "SIN_UTILIZACION_SISTEMA_FINANCIERO", true, false)));
        when(repository.findFormaPago("99")).thenReturn(Mono.empty());

        StepVerifier.create(service.existeFormaPago("01")).expectNext(true).verifyComplete();
        StepVerifier.create(service.existeFormaPago("99")).expectNext(false).verifyComplete();
    }

    @Test
    @DisplayName("esBancarizada — refleja el flag del catálogo; un código inexistente es false")
    void esBancarizada_reflejaElFlagDelCatalogo() {
        when(repository.findFormaPago("01"))
                .thenReturn(Mono.just(new FormaPagoSri("01", "SIN_UTILIZACION_SISTEMA_FINANCIERO", true, false)));
        when(repository.findFormaPago("19"))
                .thenReturn(Mono.just(new FormaPagoSri("19", "TARJETA_CREDITO", true, true)));
        when(repository.findFormaPago("99")).thenReturn(Mono.empty());

        StepVerifier.create(service.esBancarizada("19")).expectNext(true).verifyComplete();
        StepVerifier.create(service.esBancarizada("01")).expectNext(false).verifyComplete();
        // Un código inexistente no explota: lo rechaza antes existeFormaPago().
        StepVerifier.create(service.esBancarizada("99")).expectNext(false).verifyComplete();
    }

    @Test
    @DisplayName("existeFormaPago — el hit y el miss se cachean; una sola llamada al repo por código")
    void existeFormaPago_cacheaHitYMiss() {
        when(repository.findFormaPago("01"))
                .thenReturn(Mono.just(new FormaPagoSri("01", "SIN_UTILIZACION_SISTEMA_FINANCIERO", true, false)));
        when(repository.findFormaPago("99")).thenReturn(Mono.empty());

        // Hit repetido
        StepVerifier.create(service.existeFormaPago("01")).expectNext(true).verifyComplete();
        StepVerifier.create(service.existeFormaPago("01")).expectNext(true).verifyComplete();
        // Miss repetido
        StepVerifier.create(service.existeFormaPago("99")).expectNext(false).verifyComplete();
        StepVerifier.create(service.existeFormaPago("99")).expectNext(false).verifyComplete();

        verify(repository, times(1)).findFormaPago("01");
        verify(repository, times(1)).findFormaPago("99");
    }

    @Test
    @DisplayName("existeTipoIdentificacion — smoke test de lookup")
    void existeTipoIdentificacion_ok() {
        when(repository.findTipoIdentificacion("05"))
                .thenReturn(Mono.just(new TipoIdentificacionSri("05", "CEDULA")));
        when(repository.findTipoIdentificacion("99")).thenReturn(Mono.empty());

        StepVerifier.create(service.existeTipoIdentificacion("05")).expectNext(true).verifyComplete();
        StepVerifier.create(service.existeTipoIdentificacion("99")).expectNext(false).verifyComplete();
    }

    @Test
    @DisplayName("obtenerTarifaIvaVigente — código 4 (IVA 15%) aceptado en fechas de 2026")
    void obtenerTarifaIvaVigente_codigo4_vigenteEn2026() {
        TarifaIvaSri iva15 = new TarifaIvaSri(
                "4", "IVA_15", new BigDecimal("15.00"),
                LocalDate.of(2024, 4, 1), null);
        when(repository.findTarifaIva("4")).thenReturn(Mono.just(iva15));

        StepVerifier.create(service.obtenerTarifaIvaVigente("4", LocalDate.of(2026, 7, 11)))
                .expectNext(iva15)
                .verifyComplete();
    }

    @Test
    @DisplayName("obtenerTarifaIvaVigente — código 2 (IVA 12%) rechazado si fechaEmision > 2024-03-31")
    void obtenerTarifaIvaVigente_codigo2_rechazadoDespuesDeMarzo2024() {
        TarifaIvaSri iva12 = new TarifaIvaSri(
                "2", "IVA_12", new BigDecimal("12.00"),
                LocalDate.of(2008, 1, 1), LocalDate.of(2024, 3, 31));
        when(repository.findTarifaIva("2")).thenReturn(Mono.just(iva12));

        StepVerifier.create(service.obtenerTarifaIvaVigente("2", LocalDate.of(2026, 7, 11)))
                .expectErrorSatisfies(err -> assertThat(err)
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("no está vigente"))
                .verify();
    }

    @Test
    @DisplayName("obtenerTarifaIvaVigente — código inexistente propaga BusinessException")
    void obtenerTarifaIvaVigente_codigoNoExiste_lanzaBusinessException() {
        when(repository.findTarifaIva("9")).thenReturn(Mono.empty());

        StepVerifier.create(service.obtenerTarifaIvaVigente("9", LocalDate.of(2026, 7, 11)))
                .expectErrorSatisfies(err -> assertThat(err)
                        .isInstanceOf(BusinessException.class)
                        .hasMessageContaining("9"))
                .verify();
    }

    @Test
    @DisplayName("obtenerMotivoAnulacion — código válido retorna el motivo")
    void obtenerMotivoAnulacion_codigoValido_ok() {
        MotivoAnulacionNcSri motivo = new MotivoAnulacionNcSri(1, "DEVOLUCION", "Devolución de mercadería");
        when(repository.findMotivoAnulacionNc("DEVOLUCION")).thenReturn(Mono.just(motivo));

        StepVerifier.create(service.obtenerMotivoAnulacion("DEVOLUCION"))
                .expectNext(motivo)
                .verifyComplete();
    }

    @Test
    @DisplayName("obtenerMotivoAnulacion — código inexistente propaga NotFoundException")
    void obtenerMotivoAnulacion_codigoNoExiste_lanzaNotFound() {
        when(repository.findMotivoAnulacionNc("XXX")).thenReturn(Mono.empty());

        StepVerifier.create(service.obtenerMotivoAnulacion("XXX"))
                .expectError(NotFoundException.class)
                .verify();
    }

    @Test
    @DisplayName("existeFormaPago — código null retorna false sin invocar el repositorio")
    void existeFormaPago_codigoNull_retornaFalse() {
        StepVerifier.create(service.existeFormaPago(null))
                .expectNext(false)
                .verifyComplete();
    }
}
