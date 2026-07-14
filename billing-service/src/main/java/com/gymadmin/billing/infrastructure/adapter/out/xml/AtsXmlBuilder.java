package com.gymadmin.billing.infrastructure.adapter.out.xml;

import com.gymadmin.billing.domain.model.AtsPagoComprobante;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * G9 · Construye el XML del Anexo Transaccional Simplificado (ATS).
 * <p>
 * La estructura sigue el esquema oficial del SRI (<a
 * href="https://descargas.sri.gob.ec/download/anexos/ats/ats.xsd">ats.xsd</a>, copiado
 * en {@code src/test/resources/sri/ats.xsd} y verificado por {@code AtsXmlBuilderTest}).
 * Notas del esquema que no son obvias:
 * <ul>
 *   <li>La raíz es {@code <iva>}, no {@code <ats>}, y {@code codigoOperativo} es la
 *       constante {@code "IVA"}.</li>
 *   <li>{@code detalleVentas} <strong>agrupa</strong> por (cliente, tipo de comprobante):
 *       {@code numeroComprobantes} es un <em>conteo</em>, no el número de una factura.
 *       Los importes del grupo van sumados.</li>
 *   <li>Las notas de crédito no tienen nodo propio: van en {@code detalleVentas}
 *       distinguidas por {@code tipoComprobante} ({@code "04"}).</li>
 *   <li>Los anulados van en su propio nodo {@code anulados} y quedan fuera de las
 *       ventas (la query de autorizados ya los excluye: anular mueve el estado a
 *       {@code ANULADO}).</li>
 *   <li>Las formas de pago van en {@code formasDePago} con N {@code formaPago} — por eso
 *       el viejo campo único {@code tipoPago} hardcodeado a {@code "20"} desaparece.</li>
 * </ul>
 */
@Component
public class AtsXmlBuilder {

    private static final String CODIGO_OPERATIVO = "IVA";
    /** {@code E} = emisión electrónica (la única que emite este servicio). */
    private static final String TIPO_EMISION_ELECTRONICA = "E";
    private static final String TIPO_ID_INFORMANTE_RUC = "R";

    public Mono<byte[]> buildAts(ConfigSri configSri,
                                 List<Comprobante> comprobantes,
                                 List<AtsPagoComprobante> pagos,
                                 List<Comprobante> anulados,
                                 int anio,
                                 int mes) {
        return Mono.fromCallable(() -> buildXmlBytes(configSri, comprobantes, pagos, anulados, anio, mes))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private byte[] buildXmlBytes(ConfigSri configSri,
                                 List<Comprobante> comprobantes,
                                 List<AtsPagoComprobante> pagos,
                                 List<Comprobante> anulados,
                                 int anio,
                                 int mes) {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.newDocument();

            Element iva = doc.createElement("iva");
            doc.appendChild(iva);

            // Las NC (tipo 04) restan del total de ventas: su importe se reporta en
            // negativo. totalVentasType admite el signo menos justamente por esto.
            BigDecimal totalVentas = comprobantes.stream()
                    .map(this::totalConSigno)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            addElement(doc, iva, "TipoIDInformante", TIPO_ID_INFORMANTE_RUC);
            addElement(doc, iva, "IdInformante", safe(configSri.getRuc()));
            addElement(doc, iva, "razonSocial", safe(configSri.getRazonSocial()));
            addElement(doc, iva, "Anio", String.valueOf(anio));
            addElement(doc, iva, "Mes", String.format("%02d", mes));
            addElement(doc, iva, "numEstabRuc", numEstabRuc(comprobantes, anulados));
            addElement(doc, iva, "totalVentas", formatAmount(totalVentas));
            addElement(doc, iva, "codigoOperativo", CODIGO_OPERATIVO);

            // El orden de los hijos es significativo para el XSD (xsd:sequence):
            // ventas → ventasEstablecimiento → anulados.
            appendVentas(doc, iva, comprobantes, pagos);
            appendVentasEstablecimiento(doc, iva, comprobantes);
            appendAnulados(doc, iva, anulados);

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

    /**
     * Agrupa las ventas por (tipo de identificación, identificación, tipo de
     * comprobante) — la clave que exige el ATS — y emite un {@code detalleVentas} por
     * grupo con los importes sumados y el conteo de comprobantes.
     */
    private void appendVentas(Document doc, Element iva,
                              List<Comprobante> comprobantes,
                              List<AtsPagoComprobante> pagos) {
        if (comprobantes.isEmpty()) {
            return;
        }

        Map<Long, Set<String>> formasPorComprobante = pagos.stream().collect(Collectors.groupingBy(
                AtsPagoComprobante::idComprobante,
                Collectors.mapping(AtsPagoComprobante::formaPago, Collectors.toCollection(TreeSet::new))));

        // LinkedHashMap: preserva el orden de aparición para que el XML sea determinista.
        Map<ClaveVenta, List<Comprobante>> grupos = new LinkedHashMap<>();
        for (Comprobante c : comprobantes) {
            grupos.computeIfAbsent(
                    new ClaveVenta(safe(c.getTipoIdReceptor()), safe(c.getIdReceptor()), safe(c.getTipoComprobante())),
                    k -> new ArrayList<>()).add(c);
        }

        Element ventas = doc.createElement("ventas");
        iva.appendChild(ventas);

        grupos.forEach((clave, delGrupo) ->
                ventas.appendChild(buildDetalleVentas(doc, clave, delGrupo, formasPorComprobante)));
    }

    private Element buildDetalleVentas(Document doc,
                                       ClaveVenta clave,
                                       List<Comprobante> grupo,
                                       Map<Long, Set<String>> formasPorComprobante) {
        Element detalle = doc.createElement("detalleVentas");

        BigDecimal baseImponible = sumar(grupo, Comprobante::getSubtotalSinImpuesto);
        BigDecimal montoIva = sumar(grupo, Comprobante::getTotalIva);
        BigDecimal montoIce = sumar(grupo, Comprobante::getTotalIce);

        addElement(doc, detalle, "tpIdCliente", clave.tipoIdCliente());
        addElement(doc, detalle, "idCliente", clave.idCliente());
        addElement(doc, detalle, "parteRelVtas", "NO");
        addElement(doc, detalle, "tipoComprobante", clave.tipoComprobante());
        addElement(doc, detalle, "tipoEmision", TIPO_EMISION_ELECTRONICA);
        addElement(doc, detalle, "numeroComprobantes", String.valueOf(grupo.size()));
        addElement(doc, detalle, "baseNoGraIva", formatAmount(BigDecimal.ZERO));
        addElement(doc, detalle, "baseImponible", formatAmount(BigDecimal.ZERO));
        // Todo lo que factura el gym (membresías) grava IVA; la base no gravada y la
        // base imponible tarifa 0 quedan en cero hasta que G6-follow exponga la
        // tarifa por línea de detalle.
        addElement(doc, detalle, "baseImpGrav", formatAmount(baseImponible));
        addElement(doc, detalle, "montoIva", formatAmount(montoIva));
        addElement(doc, detalle, "montoIce", formatAmount(montoIce));
        // El gym no es agente de retención (G8 lo habilitaría): sin retenciones en ventas.
        addElement(doc, detalle, "valorRetIva", formatAmount(BigDecimal.ZERO));
        addElement(doc, detalle, "valorRetRenta", formatAmount(BigDecimal.ZERO));

        appendFormasDePago(doc, detalle, grupo, formasPorComprobante);

        return detalle;
    }

    /**
     * {@code formasDePago} lleva los códigos distintos usados por los comprobantes del
     * grupo. El nodo exige al menos un {@code formaPago}, así que se omite entero si
     * ninguno registró pagos (comprobantes viejos previos a la tabla de pagos).
     */
    private void appendFormasDePago(Document doc, Element detalle,
                                    List<Comprobante> grupo,
                                    Map<Long, Set<String>> formasPorComprobante) {
        Set<String> codigos = grupo.stream()
                .map(Comprobante::getId)
                .map(id -> formasPorComprobante.getOrDefault(id, Set.of()))
                .flatMap(Set::stream)
                .collect(Collectors.toCollection(TreeSet::new));

        if (codigos.isEmpty()) {
            return;
        }

        Element formasDePago = doc.createElement("formasDePago");
        detalle.appendChild(formasDePago);
        codigos.forEach(codigo -> addElement(doc, formasDePago, "formaPago", codigo));
    }

    /** Total de ventas por establecimiento — un {@code ventaEst} por código distinto. */
    private void appendVentasEstablecimiento(Document doc, Element iva, List<Comprobante> comprobantes) {
        if (comprobantes.isEmpty()) {
            return;
        }

        Map<String, BigDecimal> porEstablecimiento = new LinkedHashMap<>();
        for (Comprobante c : comprobantes) {
            porEstablecimiento.merge(safe(c.getCodEstablecimiento()), totalConSigno(c), BigDecimal::add);
        }

        Element ventasEstablecimiento = doc.createElement("ventasEstablecimiento");
        iva.appendChild(ventasEstablecimiento);

        porEstablecimiento.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    Element ventaEst = doc.createElement("ventaEst");
                    ventasEstablecimiento.appendChild(ventaEst);
                    addElement(doc, ventaEst, "codEstab", entry.getKey());
                    addElement(doc, ventaEst, "ventasEstab", formatAmount(entry.getValue()));
                });
    }

    /**
     * Nodo {@code anulados}. El XSD pide un rango {@code secuencialInicio}–
     * {@code secuencialFin}; emitimos una entrada por comprobante (rango de uno solo),
     * que es válido y evita inventar rangos con huecos.
     */
    private void appendAnulados(Document doc, Element iva, List<Comprobante> anulados) {
        if (anulados.isEmpty()) {
            return;
        }

        Element nodo = doc.createElement("anulados");
        iva.appendChild(nodo);

        anulados.stream()
                .sorted(Comparator.comparing(c -> safe(c.getSecuencial())))
                .forEach(c -> {
                    Element detalle = doc.createElement("detalleAnulados");
                    nodo.appendChild(detalle);
                    addElement(doc, detalle, "tipoComprobante", safe(c.getTipoComprobante()));
                    addElement(doc, detalle, "establecimiento", safe(c.getCodEstablecimiento()));
                    addElement(doc, detalle, "puntoEmision", safe(c.getCodPuntoEmision()));
                    addElement(doc, detalle, "secuencialInicio", safe(c.getSecuencial()));
                    addElement(doc, detalle, "secuencialFin", safe(c.getSecuencial()));
                    addElement(doc, detalle, "autorizacion", safe(c.getNumeroAutorizacion()));
                });
    }

    /**
     * El importe de una nota de crédito (tipo 04) resta del total de ventas: se
     * reporta con signo negativo.
     */
    private BigDecimal totalConSigno(Comprobante c) {
        BigDecimal total = c.getTotal() != null ? c.getTotal() : BigDecimal.ZERO;
        return esNotaCredito(c) ? total.negate() : total;
    }

    private boolean esNotaCredito(Comprobante c) {
        return "04".equals(c.getTipoComprobante());
    }

    /**
     * Suma un campo monetario del grupo <strong>en positivo</strong>.
     * <p>
     * Los importes de {@code detalleVentas} son de tipo {@code monedaType}, cuyo patrón
     * ({@code [0-9]{1,12}\.[0-9]{2}}) <em>no admite signo negativo</em>: una nota de
     * crédito se reporta con sus valores en positivo y es su {@code tipoComprobante}
     * ({@code "04"}) el que le dice al SRI que resta. Solo el {@code totalVentas} global
     * es {@code totalVentasType}, que sí acepta el signo — ver {@link #totalConSigno}.
     */
    private BigDecimal sumar(List<Comprobante> grupo, java.util.function.Function<Comprobante, BigDecimal> campo) {
        return grupo.stream()
                .map(campo)
                .map(valor -> valor != null ? valor : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Código de establecimiento del informante; "001" si el período no tuvo movimiento. */
    private String numEstabRuc(List<Comprobante> comprobantes, List<Comprobante> anulados) {
        return comprobantes.stream()
                .findFirst()
                .or(() -> anulados.stream().findFirst())
                .map(c -> safe(c.getCodEstablecimiento()))
                .filter(s -> !s.isEmpty())
                .orElse("001");
    }

    private void addElement(Document doc, Element parent, String tagName, String textContent) {
        Element element = doc.createElement(tagName);
        element.setTextContent(textContent != null ? textContent : "");
        parent.appendChild(element);
    }

    private String formatAmount(BigDecimal value) {
        if (value == null) return "0.00";
        return value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    /** Clave de agrupación de {@code detalleVentas}. */
    private record ClaveVenta(String tipoIdCliente, String idCliente, String tipoComprobante) {}
}
