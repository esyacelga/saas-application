package com.gymadmin.billing.infrastructure.adapter.out.xml;

import com.gymadmin.billing.domain.model.Comprobante;
import com.gymadmin.billing.domain.model.ComprobanteDetalle;
import com.gymadmin.billing.domain.model.ConfigSri;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Builds SRI Ecuador factura XML (version 2.24) using DOM API exclusively.
 * No string concatenation or JAXB.
 * <p>
 * La versión se declara en la constante {@link #XML_VERSION}. Ver
 * {@code docs/billing-service/pendientes/adr/001-version-xml-sri.md} para el
 * ADR que justifica la elección de v2.24 sobre v2.1.0 / v2.2.0 / v2.30 / v2.32.
 */
@Component
public class FacturaXmlBuilder {

    /**
     * Versión de la ficha técnica SRI declarada en el atributo {@code version}
     * del elemento raíz {@code <factura>}. Ver ADR 001.
     */
    static final String XML_VERSION = "2.24";

    private static final DateTimeFormatter FECHA_FACTURA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public record ImpuestoTotal(
            String codigo,
            String codigoPorcentaje,
            BigDecimal baseImponible,
            BigDecimal valor
    ) {}

    public record ImpuestoDetalle(
            String codigo,
            String codigoPorcentaje,
            BigDecimal tarifa,
            BigDecimal baseImponible,
            BigDecimal valor
    ) {}

    public record Pago(
            String formaPago,
            BigDecimal total
    ) {}

    public String buildXml(
            Comprobante comprobante,
            List<ComprobanteDetalle> detalles,
            ConfigSri configSri,
            List<ImpuestoTotal> impuestosTotales,
            List<Pago> pagos) {

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.newDocument();

            Element factura = doc.createElement("factura");
            factura.setAttribute("id", "comprobante");
            factura.setAttribute("version", XML_VERSION);
            doc.appendChild(factura);

            factura.appendChild(buildInfoTributaria(doc, comprobante, configSri));
            factura.appendChild(buildInfoFactura(doc, comprobante, configSri, impuestosTotales, pagos));
            factura.appendChild(buildDetalles(doc, detalles));
            factura.appendChild(buildInfoAdicional(doc, comprobante));

            return serializeDocument(doc);

        } catch (ParserConfigurationException | TransformerException e) {
            throw new IllegalStateException("Error al construir el XML de la factura", e);
        }
    }

    private Element buildInfoTributaria(Document doc, Comprobante comprobante, ConfigSri configSri) {
        Element infoTributaria = doc.createElement("infoTributaria");

        addElement(doc, infoTributaria, "ambiente", configSri.getAmbiente());
        addElement(doc, infoTributaria, "tipoEmision", "1");
        addElement(doc, infoTributaria, "razonSocial", configSri.getRazonSocial());
        addElement(doc, infoTributaria, "nombreComercial", configSri.getNombreComercial());
        addElement(doc, infoTributaria, "ruc", configSri.getRuc());
        addElement(doc, infoTributaria, "claveAcceso", comprobante.getClaveAcceso());
        addElement(doc, infoTributaria, "codDoc", comprobante.getTipoComprobante());
        addElement(doc, infoTributaria, "estab", comprobante.getCodEstablecimiento());
        addElement(doc, infoTributaria, "ptoEmi", comprobante.getCodPuntoEmision());
        addElement(doc, infoTributaria, "secuencial", comprobante.getSecuencial());
        addElement(doc, infoTributaria, "dirMatriz", configSri.getDirEstablecimiento());

        return infoTributaria;
    }

    private Element buildInfoFactura(Document doc, Comprobante comprobante, ConfigSri configSri,
                                      List<ImpuestoTotal> impuestosTotales, List<Pago> pagos) {
        Element infoFactura = doc.createElement("infoFactura");

        addElement(doc, infoFactura, "fechaEmision", comprobante.getFechaEmision().format(FECHA_FACTURA));
        addElement(doc, infoFactura, "dirEstablecimiento", configSri.getDirEstablecimiento());

        if (configSri.getContribuyenteEspecial() != null && !configSri.getContribuyenteEspecial().isBlank()) {
            addElement(doc, infoFactura, "contribuyenteEspecial", configSri.getContribuyenteEspecial());
        }

        addElement(doc, infoFactura, "obligadoContabilidad",
                Boolean.TRUE.equals(configSri.getObligadoContabilidad()) ? "SI" : "NO");
        addElement(doc, infoFactura, "tipoIdentificacionComprador", comprobante.getTipoIdReceptor());
        addElement(doc, infoFactura, "razonSocialComprador", comprobante.getRazonSocialReceptor());
        addElement(doc, infoFactura, "identificacionComprador", comprobante.getIdReceptor());
        addElement(doc, infoFactura, "totalSinImpuestos",
                formatAmount(comprobante.getSubtotalSinImpuesto()));
        addElement(doc, infoFactura, "totalDescuento",
                formatAmount(comprobante.getTotalDescuento() != null ? comprobante.getTotalDescuento() : BigDecimal.ZERO));

        Element totalConImpuestos = doc.createElement("totalConImpuestos");
        for (ImpuestoTotal impuesto : impuestosTotales) {
            Element totalImpuesto = doc.createElement("totalImpuesto");
            addElement(doc, totalImpuesto, "codigo", impuesto.codigo());
            addElement(doc, totalImpuesto, "codigoPorcentaje", impuesto.codigoPorcentaje());
            addElement(doc, totalImpuesto, "baseImponible", formatAmount(impuesto.baseImponible()));
            addElement(doc, totalImpuesto, "valor", formatAmount(impuesto.valor()));
            totalConImpuestos.appendChild(totalImpuesto);
        }
        infoFactura.appendChild(totalConImpuestos);

        addElement(doc, infoFactura, "propina",
                formatAmount(comprobante.getPropina() != null ? comprobante.getPropina() : BigDecimal.ZERO));
        addElement(doc, infoFactura, "importeTotal", formatAmount(comprobante.getTotal()));
        addElement(doc, infoFactura, "moneda", comprobante.getMoneda());

        Element pagosElement = doc.createElement("pagos");
        for (Pago pago : pagos) {
            Element pagoElement = doc.createElement("pago");
            addElement(doc, pagoElement, "formaPago", pago.formaPago());
            addElement(doc, pagoElement, "total", formatAmount(pago.total()));
            pagosElement.appendChild(pagoElement);
        }
        infoFactura.appendChild(pagosElement);

        return infoFactura;
    }

    private Element buildDetalles(Document doc, List<ComprobanteDetalle> detalles) {
        Element detallesElement = doc.createElement("detalles");

        for (ComprobanteDetalle detalle : detalles) {
            Element detalleElement = doc.createElement("detalle");

            addElement(doc, detalleElement, "codigoPrincipal", detalle.getCodigoPrincipal());
            if (detalle.getCodigoAuxiliar() != null && !detalle.getCodigoAuxiliar().isBlank()) {
                addElement(doc, detalleElement, "codigoAuxiliar", detalle.getCodigoAuxiliar());
            }
            addElement(doc, detalleElement, "descripcion", detalle.getDescripcion());
            addElement(doc, detalleElement, "cantidad", formatQuantity(detalle.getCantidad()));
            addElement(doc, detalleElement, "precioUnitario", formatQuantity(detalle.getPrecioUnitario()));
            addElement(doc, detalleElement, "descuento",
                    formatAmount(detalle.getDescuento() != null ? detalle.getDescuento() : BigDecimal.ZERO));
            addElement(doc, detalleElement, "precioTotalSinImpuesto",
                    formatAmount(detalle.getPrecioTotalSinImpuesto()));

            // Default IVA 15% impuesto for each line item
            Element impuestosElement = doc.createElement("impuestos");
            Element impuestoElement = doc.createElement("impuesto");
            addElement(doc, impuestoElement, "codigo", "2");
            addElement(doc, impuestoElement, "codigoPorcentaje", "4");
            addElement(doc, impuestoElement, "tarifa", "15.00");
            addElement(doc, impuestoElement, "baseImponible", formatAmount(detalle.getPrecioTotalSinImpuesto()));
            BigDecimal valorIva = detalle.getPrecioTotalSinImpuesto()
                    .multiply(new BigDecimal("0.15"))
                    .setScale(2, java.math.RoundingMode.HALF_UP);
            addElement(doc, impuestoElement, "valor", formatAmount(valorIva));
            impuestosElement.appendChild(impuestoElement);
            detalleElement.appendChild(impuestosElement);

            detallesElement.appendChild(detalleElement);
        }

        return detallesElement;
    }

    private Element buildInfoAdicional(Document doc, Comprobante comprobante) {
        Element infoAdicional = doc.createElement("infoAdicional");

        if (comprobante.getEmailReceptor() != null && !comprobante.getEmailReceptor().isBlank()) {
            Element campoEmail = doc.createElement("campoAdicional");
            campoEmail.setAttribute("nombre", "Email");
            campoEmail.setTextContent(comprobante.getEmailReceptor());
            infoAdicional.appendChild(campoEmail);
        }

        if (comprobante.getTelefonoReceptor() != null && !comprobante.getTelefonoReceptor().isBlank()) {
            Element campoTel = doc.createElement("campoAdicional");
            campoTel.setAttribute("nombre", "Telefono");
            campoTel.setTextContent(comprobante.getTelefonoReceptor());
            infoAdicional.appendChild(campoTel);
        }

        if (comprobante.getDireccionReceptor() != null && !comprobante.getDireccionReceptor().isBlank()) {
            Element campoDireccion = doc.createElement("campoAdicional");
            campoDireccion.setAttribute("nombre", "Direccion");
            campoDireccion.setTextContent(comprobante.getDireccionReceptor());
            infoAdicional.appendChild(campoDireccion);
        }

        return infoAdicional;
    }

    private void addElement(Document doc, Element parent, String tagName, String textContent) {
        Element element = doc.createElement(tagName);
        element.setTextContent(textContent != null ? textContent : "");
        parent.appendChild(element);
    }

    private String formatAmount(BigDecimal value) {
        if (value == null) return "0.00";
        return String.format("%.2f", value);
    }

    private String formatQuantity(BigDecimal value) {
        if (value == null) return "0.000000";
        return String.format("%.6f", value);
    }

    private String serializeDocument(Document doc) throws TransformerException {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.VERSION, "1.0");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }
}
