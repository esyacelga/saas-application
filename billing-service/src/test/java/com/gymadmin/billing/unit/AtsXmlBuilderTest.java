package com.gymadmin.billing.unit;

import com.gymadmin.billing.domain.model.AtsPagoComprobante;
import com.gymadmin.billing.domain.model.Comprobante;
import com.gymadmin.billing.domain.model.ConfigSri;
import com.gymadmin.billing.infrastructure.adapter.out.xml.AtsXmlBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * G9 · El ATS se valida contra el XSD oficial del SRI
 * ({@code src/test/resources/sri/ats.xsd}, descargado de
 * {@code https://descargas.sri.gob.ec/download/anexos/ats/ats.xsd}).
 * <p>
 * Validar contra el esquema real es el punto del test: la implementación anterior
 * emitía una raíz {@code <ats>} con nombres de campo inventados
 * ({@code tipoComp}, {@code numComp}, {@code tipoPago}…) que el SRI habría rechazado.
 */
@DisplayName("G9 · AtsXmlBuilder — estructura válida contra el XSD oficial del SRI")
class AtsXmlBuilderTest {

    private AtsXmlBuilder builder;
    private Validator validator;

    @BeforeEach
    void setUp() throws SAXException {
        builder = new AtsXmlBuilder();

        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        InputStream xsd = getClass().getResourceAsStream("/sri/ats.xsd");
        assertThat(xsd).as("ats.xsd debe estar en src/test/resources/sri/").isNotNull();
        Schema schema = factory.newSchema(new javax.xml.transform.stream.StreamSource(xsd));
        validator = schema.newValidator();
    }

    @Test
    @DisplayName("una factura con pago produce un XML que valida contra el XSD")
    void facturaSimple_validaContraXsd() throws Exception {
        byte[] xml = builder.buildAts(
                config(),
                List.of(factura(1L, "500.00", "60.00")),
                List.of(new AtsPagoComprobante(1L, "19")),
                List.of(),
                2026, 7
        ).block();

        assertThatCode(() -> validar(xml)).doesNotThrowAnyException();

        Document doc = parse(xml);
        assertThat(doc.getDocumentElement().getTagName()).isEqualTo("iva");
        assertThat(texto(doc, "codigoOperativo")).isEqualTo("IVA");
        assertThat(texto(doc, "tipoComprobante")).isEqualTo("01");
        assertThat(texto(doc, "tipoEmision")).isEqualTo("E");
        assertThat(texto(doc, "formaPago")).isEqualTo("19");
    }

    @Test
    @DisplayName("una nota de crédito resta del total de ventas y se reporta como tipo 04")
    void notaCredito_restaDelTotalYSeReportaComoTipo04() throws Exception {
        // Factura de 500 + NC de 100 sobre otro cliente → total de ventas 400.
        byte[] xml = builder.buildAts(
                config(),
                List.of(factura(1L, "500.00", "60.00"), notaCredito(2L, "100.00", "12.00")),
                List.of(new AtsPagoComprobante(1L, "19"), new AtsPagoComprobante(2L, "19")),
                List.of(),
                2026, 7
        ).block();

        assertThatCode(() -> validar(xml)).doesNotThrowAnyException();

        Document doc = parse(xml);
        assertThat(texto(doc, "totalVentas")).isEqualTo("400.00");

        // Las NC no tienen nodo propio: van en detalleVentas con tipoComprobante 04.
        NodeList tipos = doc.getElementsByTagName("tipoComprobante");
        List<String> valores = new java.util.ArrayList<>();
        for (int i = 0; i < tipos.getLength(); i++) {
            valores.add(tipos.item(i).getTextContent());
        }
        assertThat(valores).containsExactlyInAnyOrder("01", "04");
    }

    @Test
    @DisplayName("agrupa por cliente + tipo: numeroComprobantes es un conteo, no un número de factura")
    void agrupaPorClienteYTipo_numeroComprobantesEsConteo() throws Exception {
        // Tres facturas del mismo cliente → un solo detalleVentas con conteo 3.
        byte[] xml = builder.buildAts(
                config(),
                List.of(factura(1L, "100.00", "12.00"),
                        factura(2L, "200.00", "24.00"),
                        factura(3L, "300.00", "36.00")),
                List.of(new AtsPagoComprobante(1L, "19"),
                        new AtsPagoComprobante(2L, "01"),
                        new AtsPagoComprobante(3L, "19")),
                List.of(),
                2026, 7
        ).block();

        assertThatCode(() -> validar(xml)).doesNotThrowAnyException();

        Document doc = parse(xml);
        assertThat(doc.getElementsByTagName("detalleVentas").getLength()).isEqualTo(1);
        assertThat(texto(doc, "numeroComprobantes")).isEqualTo("3");
        // Los importes del grupo van sumados. baseImpGrav es la base imponible
        // (sin IVA): 600 de total − 72 de IVA = 528.
        assertThat(texto(doc, "baseImpGrav")).isEqualTo("528.00");
        assertThat(texto(doc, "montoIva")).isEqualTo("72.00");
        // Y las formas de pago distintas del grupo, deduplicadas.
        assertThat(doc.getElementsByTagName("formaPago").getLength()).isEqualTo(2);
    }

    @Test
    @DisplayName("los anulados van en su propio nodo, no como una venta")
    void anulados_vanEnNodoPropio() throws Exception {
        Comprobante anulado = factura(9L, "80.00", "9.60");
        anulado.setEstado("ANULADO");
        anulado.setNumeroAutorizacion("2607202601179012345600110010010000000091234567813");

        byte[] xml = builder.buildAts(
                config(),
                List.of(factura(1L, "500.00", "60.00")),
                List.of(new AtsPagoComprobante(1L, "19")),
                List.of(anulado),
                2026, 7
        ).block();

        assertThatCode(() -> validar(xml)).doesNotThrowAnyException();

        Document doc = parse(xml);
        assertThat(doc.getElementsByTagName("detalleAnulados").getLength()).isEqualTo(1);
        // El anulado no infla el total de ventas.
        assertThat(texto(doc, "totalVentas")).isEqualTo("500.00");
        assertThat(texto(doc, "secuencialInicio")).isEqualTo(texto(doc, "secuencialFin"));
    }

    @Test
    @DisplayName("un mes sin movimiento produce un XML válido (sin nodos de venta)")
    void mesVacio_validaContraXsd() throws Exception {
        byte[] xml = builder.buildAts(config(), List.of(), List.of(), List.of(), 2026, 7).block();

        assertThatCode(() -> validar(xml)).doesNotThrowAnyException();

        Document doc = parse(xml);
        assertThat(texto(doc, "totalVentas")).isEqualTo("0.00");
        assertThat(texto(doc, "numEstabRuc")).isEqualTo("001");
    }

    // ─── helpers ─────────────────────────────────────────────────────────────────

    private void validar(byte[] xml) throws Exception {
        validator.validate(new DOMSource(parse(xml)));
    }

    private Document parse(byte[] xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);
        return dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
    }

    private String texto(Document doc, String tag) {
        NodeList nodes = doc.getElementsByTagName(tag);
        assertThat(nodes.getLength()).as("no se encontró el nodo <%s>", tag).isGreaterThan(0);
        Node node = nodes.item(0);
        return node.getTextContent();
    }

    private ConfigSri config() {
        return ConfigSri.builder()
                .idCompania(1)
                .idSucursal(1)
                .ruc("1790012345001")
                .razonSocial("Gimnasio Test")
                .ambiente("1")
                .build();
    }

    private Comprobante factura(Long id, String total, String iva) {
        return comprobante(id, "01", total, iva);
    }

    private Comprobante notaCredito(Long id, String total, String iva) {
        return comprobante(id, "04", total, iva);
    }

    private Comprobante comprobante(Long id, String tipo, String total, String iva) {
        BigDecimal totalBd = new BigDecimal(total);
        BigDecimal ivaBd = new BigDecimal(iva);
        return Comprobante.builder()
                .id(id)
                .idCompania(1)
                .idSucursal(1)
                .tipoComprobante(tipo)
                .codEstablecimiento("001")
                .codPuntoEmision("001")
                .secuencial(String.format("%09d", id))
                .fechaEmision(LocalDate.of(2026, 7, 15))
                .tipoIdReceptor("05")
                .idReceptor("1712345678")
                .razonSocialReceptor("Cliente Test")
                .subtotalSinImpuesto(totalBd.subtract(ivaBd))
                .totalIva(ivaBd)
                .totalIce(BigDecimal.ZERO)
                .total(totalBd)
                .estado("AUTORIZADO")
                .build();
    }
}
