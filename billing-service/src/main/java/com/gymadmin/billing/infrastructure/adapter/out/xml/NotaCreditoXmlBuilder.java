package com.gymadmin.billing.infrastructure.adapter.out.xml;

import com.gymadmin.billing.domain.model.Comprobante;
import com.gymadmin.billing.domain.model.ComprobanteDetalle;
import com.gymadmin.billing.domain.model.ConfigSri;
import com.gymadmin.billing.domain.model.NotaCreditoReferencia;
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
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Construye el XML de la nota de crédito SRI Ecuador (ficha técnica NC v1.1.0)
 * usando la API DOM. Sigue el mismo patrón que {@link FacturaXmlBuilder}: sin
 * concatenación de strings, sin JAXB.
 * <p>
 * TODO(G6-follow): la tarifa IVA por línea sigue hardcodeada al 15% (código
 * {@code "2"}, porcentaje {@code "4"}, tarifa {@code 15.00}) igual que en
 * {@link FacturaXmlBuilder}. Se resolverá cuando el catálogo
 * {@code sri.tarifas_iva} sea consultable por detalle.
 */
@Component
public class NotaCreditoXmlBuilder {

    /** Versión de la ficha técnica SRI para NC (tipo {@code "04"}). */
    static final String XML_VERSION_NC = "1.1.0";

    private static final DateTimeFormatter FECHA_NC = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public String buildXml(
            Comprobante comprobante,
            List<ComprobanteDetalle> detalles,
            ConfigSri configSri,
            NotaCreditoReferencia referencia,
            List<FacturaXmlBuilder.ImpuestoTotal> impuestosTotales) {

        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.newDocument();

            Element notaCredito = doc.createElement("notaCredito");
            notaCredito.setAttribute("id", "comprobante");
            notaCredito.setAttribute("version", XML_VERSION_NC);
            doc.appendChild(notaCredito);

            notaCredito.appendChild(buildInfoTributaria(doc, comprobante, configSri));
            notaCredito.appendChild(buildInfoNotaCredito(doc, comprobante, configSri, referencia, impuestosTotales));
            notaCredito.appendChild(buildDetalles(doc, detalles));
            notaCredito.appendChild(buildInfoAdicional(doc, comprobante));

            return serializeDocument(doc);

        } catch (ParserConfigurationException | TransformerException e) {
            throw new IllegalStateException("Error al construir el XML de la nota de crédito", e);
        }
    }

    private Element buildInfoTributaria(Document doc, Comprobante comprobante, ConfigSri configSri) {
        Element infoTributaria = doc.createElement("infoTributaria");

        addElement(doc, infoTributaria, "ambiente", configSri.getAmbiente());
        addElement(doc, infoTributaria, "tipoEmision", "1");
        addElement(doc, infoTributaria, "razonSocial", configSri.getRazonSocial());
        if (configSri.getNombreComercial() != null && !configSri.getNombreComercial().isBlank()) {
            addElement(doc, infoTributaria, "nombreComercial", configSri.getNombreComercial());
        }
        addElement(doc, infoTributaria, "ruc", configSri.getRuc());
        addElement(doc, infoTributaria, "claveAcceso", comprobante.getClaveAcceso());
        addElement(doc, infoTributaria, "codDoc", comprobante.getTipoComprobante());
        addElement(doc, infoTributaria, "estab", comprobante.getCodEstablecimiento());
        addElement(doc, infoTributaria, "ptoEmi", comprobante.getCodPuntoEmision());
        addElement(doc, infoTributaria, "secuencial", comprobante.getSecuencial());
        addElement(doc, infoTributaria, "dirMatriz", configSri.getDirEstablecimiento());

        return infoTributaria;
    }

    private Element buildInfoNotaCredito(Document doc, Comprobante comprobante, ConfigSri configSri,
                                          NotaCreditoReferencia referencia,
                                          List<FacturaXmlBuilder.ImpuestoTotal> impuestosTotales) {
        Element infoNotaCredito = doc.createElement("infoNotaCredito");

        addElement(doc, infoNotaCredito, "fechaEmision", comprobante.getFechaEmision().format(FECHA_NC));
        addElement(doc, infoNotaCredito, "dirEstablecimiento", configSri.getDirEstablecimiento());
        addElement(doc, infoNotaCredito, "tipoIdentificacionComprador", comprobante.getTipoIdReceptor());
        addElement(doc, infoNotaCredito, "razonSocialComprador", comprobante.getRazonSocialReceptor());
        addElement(doc, infoNotaCredito, "identificacionComprador", comprobante.getIdReceptor());

        if (configSri.getContribuyenteEspecial() != null && !configSri.getContribuyenteEspecial().isBlank()) {
            addElement(doc, infoNotaCredito, "contribuyenteEspecial", configSri.getContribuyenteEspecial());
        }

        addElement(doc, infoNotaCredito, "obligadoContabilidad",
                Boolean.TRUE.equals(configSri.getObligadoContabilidad()) ? "SI" : "NO");

        addElement(doc, infoNotaCredito, "codDocModificado", referencia.getCodDocModificado());
        addElement(doc, infoNotaCredito, "numDocModificado", referencia.getNumDocModificado());
        addElement(doc, infoNotaCredito, "fechaEmisionDocSustento",
                referencia.getFechaEmisionModif().format(FECHA_NC));

        addElement(doc, infoNotaCredito, "totalSinImpuestos",
                formatAmount(comprobante.getSubtotalSinImpuesto()));
        addElement(doc, infoNotaCredito, "valorModificacion",
                formatAmount(referencia.getValorModificado()));
        addElement(doc, infoNotaCredito, "moneda",
                comprobante.getMoneda() != null ? comprobante.getMoneda() : "DOLAR");

        Element totalConImpuestos = doc.createElement("totalConImpuestos");
        for (FacturaXmlBuilder.ImpuestoTotal impuesto : impuestosTotales) {
            Element totalImpuesto = doc.createElement("totalImpuesto");
            addElement(doc, totalImpuesto, "codigo", impuesto.codigo());
            addElement(doc, totalImpuesto, "codigoPorcentaje", impuesto.codigoPorcentaje());
            addElement(doc, totalImpuesto, "baseImponible", formatAmount(impuesto.baseImponible()));
            addElement(doc, totalImpuesto, "valor", formatAmount(impuesto.valor()));
            totalConImpuestos.appendChild(totalImpuesto);
        }
        infoNotaCredito.appendChild(totalConImpuestos);

        addElement(doc, infoNotaCredito, "motivo", referencia.getRazon());

        return infoNotaCredito;
    }

    private Element buildDetalles(Document doc, List<ComprobanteDetalle> detalles) {
        Element detallesElement = doc.createElement("detalles");

        for (ComprobanteDetalle detalle : detalles) {
            Element detalleElement = doc.createElement("detalle");

            addElement(doc, detalleElement, "codigoInterno", detalle.getCodigoPrincipal());
            if (detalle.getCodigoAuxiliar() != null && !detalle.getCodigoAuxiliar().isBlank()) {
                addElement(doc, detalleElement, "codigoAdicional", detalle.getCodigoAuxiliar());
            }
            addElement(doc, detalleElement, "descripcion", detalle.getDescripcion());
            addElement(doc, detalleElement, "cantidad", formatQuantity(detalle.getCantidad()));
            addElement(doc, detalleElement, "precioUnitario", formatQuantity(detalle.getPrecioUnitario()));
            addElement(doc, detalleElement, "descuento",
                    formatAmount(detalle.getDescuento() != null ? detalle.getDescuento() : BigDecimal.ZERO));
            addElement(doc, detalleElement, "precioTotalSinImpuesto",
                    formatAmount(detalle.getPrecioTotalSinImpuesto()));

            // TODO(G6-follow): sustituir hardcode IVA 15% cuando llegue codigoTarifaIva por detalle.
            Element impuestosElement = doc.createElement("impuestos");
            Element impuestoElement = doc.createElement("impuesto");
            addElement(doc, impuestoElement, "codigo", "2");
            addElement(doc, impuestoElement, "codigoPorcentaje", "4");
            addElement(doc, impuestoElement, "tarifa", "15.00");
            addElement(doc, impuestoElement, "baseImponible", formatAmount(detalle.getPrecioTotalSinImpuesto()));
            BigDecimal valorIva = detalle.getPrecioTotalSinImpuesto()
                    .multiply(new BigDecimal("0.15"))
                    .setScale(2, RoundingMode.HALF_UP);
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
