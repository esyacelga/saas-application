package com.gymadmin.billing.unit;

import com.gymadmin.billing.domain.model.Comprobante;
import com.gymadmin.billing.domain.model.ComprobanteDetalle;
import com.gymadmin.billing.domain.model.ConfigSri;
import com.gymadmin.billing.infrastructure.adapter.out.xml.FacturaXmlBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * G1 · Fase 1 — verifica que el atributo {@code version} del elemento raíz
 * {@code <factura>} refleje la versión de la ficha técnica SRI elegida en
 * {@code docs/billing-service/pendientes/adr/001-version-xml-sri.md}.
 * <p>
 * Este test cubre exclusivamente la declaración de versión y la estructura
 * mínima del root. La coherencia semántica del contenido (impuestos, catálogos)
 * se verifica en tests dedicados de otros GAPs.
 */
@DisplayName("FacturaXmlBuilder — versión de la ficha técnica declarada (G1)")
class FacturaXmlBuilderTest {

    private FacturaXmlBuilder builder;

    @BeforeEach
    void setUp() {
        builder = new FacturaXmlBuilder();
    }

    @Test
    @DisplayName("XML generado declara version=\"2.24\" en el elemento factura raíz")
    void buildXml_root_declaraVersion224() {
        String xml = builder.buildXml(
                sampleComprobante(),
                sampleDetalles(),
                sampleConfigSri(),
                List.of(new FacturaXmlBuilder.ImpuestoTotal(
                        "2", "4",
                        new BigDecimal("10.00"),
                        new BigDecimal("1.50")
                )),
                List.of(new FacturaXmlBuilder.Pago("01", new BigDecimal("11.50")))
        );

        assertThat(xml)
                .as("El XML debe declarar la versión SRI vigente (ADR 001)")
                .contains("version=\"2.24\"")
                .doesNotContain("version=\"2.1.0\"");
    }

    @Test
    @DisplayName("El elemento raíz es <factura id=\"comprobante\"> con la versión correcta")
    void buildXml_root_tieneAtributosDeIdYVersion() {
        String xml = builder.buildXml(
                sampleComprobante(),
                sampleDetalles(),
                sampleConfigSri(),
                List.of(),
                List.of(new FacturaXmlBuilder.Pago("01", new BigDecimal("11.50")))
        );

        // El orden de los atributos no está garantizado por el serializador DOM;
        // comprobamos ambos atributos por separado.
        assertThat(xml).contains("<factura");
        assertThat(xml).contains("id=\"comprobante\"");
        assertThat(xml).contains("version=\"2.24\"");
    }

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    private Comprobante sampleComprobante() {
        return Comprobante.builder()
                .idCompania(1)
                .idSucursal(1)
                .tipoComprobante("01")
                .claveAcceso("1".repeat(49))
                .codEstablecimiento("001")
                .codPuntoEmision("001")
                .secuencial("000000001")
                .fechaEmision(LocalDate.of(2026, 7, 11))
                .ambiente("1")
                .tipoIdReceptor("05")
                .idReceptor("1712345678")
                .razonSocialReceptor("Cliente Test")
                .subtotalSinImpuesto(new BigDecimal("10.00"))
                .totalDescuento(BigDecimal.ZERO)
                .propina(BigDecimal.ZERO)
                .total(new BigDecimal("11.50"))
                .moneda("DOLAR")
                .estado("GENERADO")
                .build();
    }

    private List<ComprobanteDetalle> sampleDetalles() {
        return List.of(ComprobanteDetalle.builder()
                .codigoPrincipal("PROD001")
                .descripcion("Membresía mensual")
                .cantidad(new BigDecimal("1.00"))
                .precioUnitario(new BigDecimal("10.00"))
                .descuento(BigDecimal.ZERO)
                .precioTotalSinImpuesto(new BigDecimal("10.00"))
                .orden(1)
                .build());
    }

    private ConfigSri sampleConfigSri() {
        return ConfigSri.builder()
                .idCompania(1)
                .idSucursal(1)
                .ruc("1234567890001")
                .razonSocial("Gym SA")
                .nombreComercial("Gym SA")
                .dirEstablecimiento("Av. Test 123")
                .ambiente("1")
                .tipoEmision("1")
                .obligadoContabilidad(Boolean.FALSE)
                .build();
    }
}
