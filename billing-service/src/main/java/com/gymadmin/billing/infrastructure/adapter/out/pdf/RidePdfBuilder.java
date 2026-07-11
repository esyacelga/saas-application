package com.gymadmin.billing.infrastructure.adapter.out.pdf;

import com.gymadmin.billing.domain.model.Comprobante;
import com.gymadmin.billing.domain.model.ComprobanteDetalle;
import com.gymadmin.billing.domain.model.ConfigSri;
import com.gymadmin.billing.domain.port.out.RidePdfPort;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class RidePdfBuilder implements RidePdfPort {

    private static final DateTimeFormatter FECHA_FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA_FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @Override
    public Mono<byte[]> generarRide(Comprobante comprobante, List<ComprobanteDetalle> detalles, ConfigSri configSri) {
        return Mono.fromCallable(() -> buildPdf(comprobante, detalles, configSri))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private byte[] buildPdf(Comprobante comprobante, List<ComprobanteDetalle> detalles, ConfigSri configSri) {
        Document document = new Document(PageSize.A4, 36, 36, 54, 36);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        PdfWriter.getInstance(document, baos);
        document.open();

        Font boldLarge = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
        Font bold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 9);
        Font smallBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 8);

        if ("1".equals(comprobante.getAmbiente())) {
            Font testEnv = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
            testEnv.setColor(Color.RED);
            PdfPTable bannerTable = new PdfPTable(1);
            bannerTable.setWidthPercentage(100);
            PdfPCell bannerCell = new PdfPCell(new Phrase("AMBIENTE DE PRUEBAS — NO VÁLIDO COMO DOCUMENTO FISCAL", testEnv));
            bannerCell.setBackgroundColor(new Color(255, 230, 230));
            bannerCell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
            bannerCell.setPadding(6);
            bannerCell.setBorderColor(Color.RED);
            bannerTable.addCell(bannerCell);
            document.add(bannerTable);
            document.add(new com.lowagie.text.Paragraph(" "));
        }

        PdfPTable header = new PdfPTable(2);
        header.setWidthPercentage(100);
        header.setWidths(new float[]{55f, 45f});

        PdfPCell emisorCell = new PdfPCell();
        emisorCell.setPadding(6);
        emisorCell.addElement(new Phrase(configSri != null ? safe(configSri.getRazonSocial()) : safe(comprobante.getRazonSocialReceptor()), boldLarge));
        emisorCell.addElement(new Phrase("Dirección Matriz: " + (configSri != null ? safe(configSri.getDirEstablecimiento()) : ""), normal));
        emisorCell.addElement(new Phrase("RUC: " + (configSri != null ? safe(configSri.getRuc()) : ""), normal));
        emisorCell.addElement(new Phrase("Obligado a llevar Contabilidad: " +
                (configSri != null && Boolean.TRUE.equals(configSri.getObligadoContabilidad()) ? "SI" : "NO"), normal));
        header.addCell(emisorCell);

        PdfPCell docCell = new PdfPCell();
        docCell.setPadding(6);
        docCell.addElement(new Phrase("FACTURA", boldLarge));
        String numero = comprobante.getCodEstablecimiento() + "-" + comprobante.getCodPuntoEmision() + "-" + comprobante.getSecuencial();
        docCell.addElement(new Phrase("Número: " + numero, bold));
        docCell.addElement(new Phrase("Fecha de Emisión: " +
                (comprobante.getFechaEmision() != null ? comprobante.getFechaEmision().format(FECHA_FORMATO) : ""), normal));
        String ambienteLabel = "2".equals(comprobante.getAmbiente()) ? "PRODUCCIÓN" : "PRUEBAS";
        docCell.addElement(new Phrase("Ambiente: " + ambienteLabel, normal));
        docCell.addElement(new Phrase("Clave de Acceso:", smallBold));
        docCell.addElement(new Phrase(safe(comprobante.getClaveAcceso()), small));
        header.addCell(docCell);

        document.add(header);
        document.add(new com.lowagie.text.Paragraph(" "));

        PdfPTable receptorTable = new PdfPTable(2);
        receptorTable.setWidthPercentage(100);
        receptorTable.setWidths(new float[]{50f, 50f});

        addHeaderCell(receptorTable, "DATOS DEL RECEPTOR", bold, 2);
        addLabelValueCells(receptorTable, "Razón Social / Nombres:", safe(comprobante.getRazonSocialReceptor()), normal);
        addLabelValueCells(receptorTable, "Tipo Identificación:", safe(comprobante.getTipoIdReceptor()), normal);
        addLabelValueCells(receptorTable, "Identificación:", safe(comprobante.getIdReceptor()), normal);
        addLabelValueCells(receptorTable, "Email:", safe(comprobante.getEmailReceptor()), normal);
        addLabelValueCells(receptorTable, "Dirección:", safe(comprobante.getDireccionReceptor()), normal);
        addLabelValueCells(receptorTable, "Teléfono:", safe(comprobante.getTelefonoReceptor()), normal);

        document.add(receptorTable);
        document.add(new com.lowagie.text.Paragraph(" "));

        PdfPTable detalleTable = new PdfPTable(6);
        detalleTable.setWidthPercentage(100);
        detalleTable.setWidths(new float[]{15f, 30f, 10f, 15f, 10f, 20f});

        addHeaderCell(detalleTable, "Código", smallBold);
        addHeaderCell(detalleTable, "Descripción", smallBold);
        addHeaderCell(detalleTable, "Cantidad", smallBold);
        addHeaderCell(detalleTable, "Precio Unitario", smallBold);
        addHeaderCell(detalleTable, "Descuento", smallBold);
        addHeaderCell(detalleTable, "Total Sin Impuesto", smallBold);

        for (ComprobanteDetalle detalle : detalles) {
            addCell(detalleTable, safe(detalle.getCodigoPrincipal()), small);
            addCell(detalleTable, safe(detalle.getDescripcion()), small);
            addCell(detalleTable, formatDecimal(detalle.getCantidad(), 2), small);
            addCell(detalleTable, formatDecimal(detalle.getPrecioUnitario(), 2), small);
            addCell(detalleTable, formatDecimal(detalle.getDescuento(), 2), small);
            addCell(detalleTable, formatDecimal(detalle.getPrecioTotalSinImpuesto(), 2), small);
        }

        document.add(detalleTable);
        document.add(new com.lowagie.text.Paragraph(" "));

        PdfPTable totalesTable = new PdfPTable(2);
        totalesTable.setWidthPercentage(60);
        totalesTable.setHorizontalAlignment(PdfPTable.ALIGN_RIGHT);
        totalesTable.setWidths(new float[]{60f, 40f});

        addTotalRow(totalesTable, "Subtotal sin Impuesto:", formatDecimal(comprobante.getSubtotalSinImpuesto(), 2), normal, bold);
        addTotalRow(totalesTable, "Total IVA 15%:", formatDecimal(comprobante.getTotalIva(), 2), normal, bold);
        addTotalRow(totalesTable, "Propina:", formatDecimal(comprobante.getPropina(), 2), normal, bold);
        addTotalRow(totalesTable, "IMPORTE TOTAL:", formatDecimal(comprobante.getTotal(), 2), bold, bold);

        String moneda = comprobante.getMoneda() != null ? comprobante.getMoneda() : "DOLAR";
        addTotalRow(totalesTable, "Forma de Pago (" + moneda + "):", formatDecimal(comprobante.getTotal(), 2), normal, normal);

        document.add(totalesTable);
        document.add(new com.lowagie.text.Paragraph(" "));

        if ("AUTORIZADO".equals(comprobante.getEstado())) {
            PdfPTable autorizacionTable = new PdfPTable(1);
            autorizacionTable.setWidthPercentage(100);

            PdfPCell authCell = new PdfPCell();
            authCell.setPadding(6);
            authCell.setBackgroundColor(new Color(230, 255, 230));
            authCell.addElement(new Phrase("Autorizado por el SRI", bold));
            authCell.addElement(new Phrase("Número de Autorización: " + safe(comprobante.getNumeroAutorizacion()), normal));
            if (comprobante.getFechaAutorizacion() != null) {
                authCell.addElement(new Phrase("Fecha de Autorización: " +
                        comprobante.getFechaAutorizacion().format(FECHA_HORA_FORMATO), normal));
            }
            authCell.addElement(new Phrase("DOCUMENTO AUTORIZADO — VÁLIDO COMO COMPROBANTE DE VENTA", bold));
            autorizacionTable.addCell(authCell);
            document.add(autorizacionTable);
        }

        document.close();
        return baos.toByteArray();
    }

    private void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(new Color(200, 200, 200));
        cell.setPadding(4);
        cell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
        table.addCell(cell);
    }

    private void addHeaderCell(PdfPTable table, String text, Font font, int colspan) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(new Color(200, 200, 200));
        cell.setPadding(4);
        cell.setColspan(colspan);
        cell.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
        table.addCell(cell);
    }

    private void addCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(3);
        table.addCell(cell);
    }

    private void addLabelValueCells(PdfPTable table, String label, String value, Font font) {
        Font labelFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9);
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setPadding(3);
        labelCell.setBackgroundColor(new Color(240, 240, 240));
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, font));
        valueCell.setPadding(3);
        table.addCell(valueCell);
    }

    private void addTotalRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setPadding(3);
        labelCell.setHorizontalAlignment(PdfPCell.ALIGN_RIGHT);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, valueFont));
        valueCell.setPadding(3);
        valueCell.setHorizontalAlignment(PdfPCell.ALIGN_RIGHT);
        table.addCell(valueCell);
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    private String formatDecimal(BigDecimal value, int scale) {
        if (value == null) return "0.00";
        return String.format("%." + scale + "f", value);
    }
}
