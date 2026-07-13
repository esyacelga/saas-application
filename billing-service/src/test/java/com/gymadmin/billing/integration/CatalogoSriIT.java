package com.gymadmin.billing.integration;

import com.gymadmin.billing.IntegrationTestBase;
import com.gymadmin.billing.application.service.CatalogoSriService;
import com.gymadmin.billing.domain.port.out.CatalogoSriRepository;
import com.gymadmin.billing.infrastructure.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que los 6 catálogos SRI se leen correctamente desde el schema
 * {@code sri} contra los seeds definidos en
 * {@code 09_insert_seed_sri.sql}.
 */
@DisplayName("CatalogoSri — lookups sobre el schema sri (G6)")
class CatalogoSriIT extends IntegrationTestBase {

    @Autowired
    private CatalogoSriRepository catalogoSriRepository;

    @Autowired
    private CatalogoSriService catalogoSriService;

    @Test
    @DisplayName("findTipoComprobante('01') → FACTURA 2.1.0 activo")
    void findTipoComprobante_factura() {
        StepVerifier.create(catalogoSriRepository.findTipoComprobante("01"))
                .assertNext(t -> {
                    assertThat(t.codigo()).isEqualTo("01");
                    assertThat(t.nombre()).isEqualTo("FACTURA");
                    assertThat(t.version()).isEqualTo("2.1.0");
                    assertThat(t.activo()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("findTipoIdentificacion('05') → CEDULA")
    void findTipoIdentificacion_cedula() {
        StepVerifier.create(catalogoSriRepository.findTipoIdentificacion("05"))
                .assertNext(t -> {
                    assertThat(t.codigo()).isEqualTo("05");
                    assertThat(t.nombre()).isEqualTo("CEDULA");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("findFormaPago('01') → SIN_UTILIZACION_SISTEMA_FINANCIERO activo")
    void findFormaPago_sinUtilizacion() {
        StepVerifier.create(catalogoSriRepository.findFormaPago("01"))
                .assertNext(f -> {
                    assertThat(f.codigo()).isEqualTo("01");
                    assertThat(f.nombre()).isEqualTo("SIN_UTILIZACION_SISTEMA_FINANCIERO");
                    assertThat(f.activo()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("findTipoImpuesto('2') → IVA")
    void findTipoImpuesto_iva() {
        StepVerifier.create(catalogoSriRepository.findTipoImpuesto("2"))
                .assertNext(t -> {
                    assertThat(t.codigo()).isEqualTo("2");
                    assertThat(t.nombre()).isEqualTo("IVA");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("findTarifaIva('4') → IVA_15 15% vigente desde 2024-04-01, sin cierre")
    void findTarifaIva_15() {
        StepVerifier.create(catalogoSriRepository.findTarifaIva("4"))
                .assertNext(t -> {
                    assertThat(t.codigo()).isEqualTo("4");
                    assertThat(t.nombre()).isEqualTo("IVA_15");
                    assertThat(t.porcentaje()).isEqualByComparingTo(new BigDecimal("15.00"));
                    assertThat(t.vigenteDesde()).isEqualTo(LocalDate.of(2024, 4, 1));
                    assertThat(t.vigenteHasta()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("findMotivoAnulacionNc('DEVOLUCION') → 'Devolución de mercadería'")
    void findMotivoAnulacionNc_devolucion() {
        StepVerifier.create(catalogoSriRepository.findMotivoAnulacionNc("DEVOLUCION"))
                .assertNext(m -> {
                    assertThat(m.codigo()).isEqualTo("DEVOLUCION");
                    assertThat(m.descripcion()).isEqualTo("Devolución de mercadería");
                    assertThat(m.id()).isNotNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("existeFormaPago('01') → true; existeFormaPago('99') → false")
    void existeFormaPago_hitYMiss() {
        StepVerifier.create(catalogoSriService.existeFormaPago("01"))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(catalogoSriService.existeFormaPago("99"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("obtenerTarifaIvaVigente('2', 2026-07-11) — código 12% no vigente → BusinessException")
    void obtenerTarifaIvaVigente_iva12FueraDeVigencia_lanzaError() {
        StepVerifier.create(catalogoSriService.obtenerTarifaIvaVigente("2", LocalDate.of(2026, 7, 11)))
                .expectError(BusinessException.class)
                .verify();
    }

    @Test
    @DisplayName("obtenerTarifaIvaVigente('4', 2026-07-11) — IVA 15% aceptado")
    void obtenerTarifaIvaVigente_iva15_aceptado() {
        StepVerifier.create(catalogoSriService.obtenerTarifaIvaVigente("4", LocalDate.of(2026, 7, 11)))
                .assertNext(t -> assertThat(t.porcentaje()).isEqualByComparingTo(new BigDecimal("15.00")))
                .verifyComplete();
    }
}
