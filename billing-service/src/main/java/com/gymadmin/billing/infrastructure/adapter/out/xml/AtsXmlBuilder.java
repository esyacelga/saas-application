package com.gymadmin.billing.infrastructure.adapter.out.xml;

import com.gymadmin.billing.domain.model.Comprobante;
import com.gymadmin.billing.domain.model.ConfigSri;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;

@Component
public class AtsXmlBuilder {

    public Mono<byte[]> buildAts(ConfigSri configSri, List<Comprobante> comprobantes, int anio, int mes) {
        return Mono.fromCallable(() -> buildXmlBytes(configSri, comprobantes, anio, mes))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private byte[] buildXmlBytes(ConfigSri configSri, List<Comprobante> comprobantes, int anio, int mes) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.newDocument();

            String mesStr = String.format("%02d", mes);

            BigDecimal totalVentas = comprobantes.stream()
                    .map(c -> c.getTotal() != null ? c.getTotal() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            Element ats = doc.createElement("ats");
            doc.appendChild(ats);

            addElement(doc, ats, "TipoIDInformante", "R");
            addElement(doc, ats, "IdInformante", safe(configSri.getRuc()));
            addElement(doc, ats, "razonSocial", safe(configSri.getRazonSocial()));
            addElement(doc, ats, "Anio", String.valueOf(anio));
            addElement(doc, ats, "Mes", mesStr);
            // ATS numEstabRuc: pick the establishment code from the first invoice (all invoices of an ATS
            // report belong to the same reporting entity). When the report is empty, fall back to "001".
            String numEstabRuc = comprobantes.isEmpty()
                    ? "001"
                    : safe(comprobantes.get(0).getCodEstablecimiento());
            addElement(doc, ats, "numEstabRuc", numEstabRuc);
            addElement(doc, ats, "totalVentas", formatAmount(totalVentas));
            addElement(doc, ats, "codigoOperacion", "B");
            addElement(doc, ats, "indCancelacion", "N");

            Element ventas = doc.createElement("ventas");
            ats.appendChild(ventas);

            for (Comprobante c : comprobantes) {
                ventas.appendChild(buildDetalleVentas(doc, c, mesStr));
            }

            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer transformer = tf.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.VERSION, "1.0");

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(doc), new StreamResult(baos));
            return baos.toByteArray();

        } catch (Exception e) {
            throw new IllegalStateException("Error al construir el XML ATS", e);
        }
    }

    private Element buildDetalleVentas(Document doc, Comprobante c, String mesStr) {
        Element detalle = doc.createElement("detalleVentas");

        String numComp = String.format("%s-%s-%s",
                safe(c.getCodEstablecimiento()),
                safe(c.getCodPuntoEmision()),
                safe(c.getSecuencial()));

        addElement(doc, detalle, "tpIdCliente", safe(c.getTipoIdReceptor()));
        addElement(doc, detalle, "idCliente", safe(c.getIdReceptor()));
        addElement(doc, detalle, "parteRel", "NO");
        // G6: usar el tipo de comprobante real leído desde el comprobante.
        // Los valores válidos son los del catálogo sri.tipos_comprobante.
        addElement(doc, detalle, "tipoComp", safe(c.getTipoComprobante()));
        // TODO(G9): la forma de pago debe leerse desde facturacion.pagos del
        // comprobante y consolidarse en el ATS (soporte de múltiples pagos,
        // NC, ND y retenciones). Se resuelve en la Fase 3 · G9. Hoy no hay
        // dato disponible en el modelo Comprobante, se mantiene '20' como
        // placeholder para no bloquear la emisión del ATS.
        addElement(doc, detalle, "tipoPago", "20");
        addElement(doc, detalle, "denoComp", safe(c.getRazonSocialReceptor()));
        addElement(doc, detalle, "mesTot", mesStr);
        addElement(doc, detalle, "numComp", numComp);
        addElement(doc, detalle, "baseNoGraIva", "0.00");
        addElement(doc, detalle, "baseImponible", formatAmount(c.getSubtotalSinImpuesto()));
        addElement(doc, detalle, "baseImpGrav", formatAmount(c.getSubtotalSinImpuesto()));
        addElement(doc, detalle, "montoIce", "0.00");
        addElement(doc, detalle, "montoIva", formatAmount(c.getTotalIva()));
        addElement(doc, detalle, "valRetBien10", "0.00");
        addElement(doc, detalle, "valRetServ20", "0.00");
        addElement(doc, detalle, "valorRetBienes", "0.00");
        addElement(doc, detalle, "valRetServ50", "0.00");
        addElement(doc, detalle, "valorRetServicios", "0.00");
        addElement(doc, detalle, "valRetServ100", "0.00");
        addElement(doc, detalle, "totbasesImpReemb", "0.00");
        addElement(doc, detalle, "estabRetencion1", "");
        addElement(doc, detalle, "ptoEmiRetencion1", "");
        addElement(doc, detalle, "secRetencion1", "");
        addElement(doc, detalle, "autRetencion1", "");
        addElement(doc, detalle, "fechaEmiRet1", "");

        return detalle;
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

    private String safe(String value) {
        return value != null ? value : "";
    }
}
