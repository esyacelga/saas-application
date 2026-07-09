package com.gymadmin.billing.infrastructure.adapter.out.email;

import com.gymadmin.billing.domain.model.Comprobante;
import com.gymadmin.billing.domain.port.out.EmailNotificationPort;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;

@Component
@Slf4j
public class EmailNotificationAdapter implements EmailNotificationPort {

    private final JavaMailSender mailSender;
    private final String from;
    private final boolean emailEnabled;

    public EmailNotificationAdapter(
            JavaMailSender mailSender,
            @Value("${app.email.from}") String from,
            @Value("${app.email.enabled:false}") boolean emailEnabled) {
        this.mailSender = mailSender;
        this.from = from;
        this.emailEnabled = emailEnabled;
    }

    @Override
    public Mono<Void> enviarFactura(Comprobante comprobante, byte[] ridePdf) {
        if (!emailEnabled) {
            return Mono.empty();
        }
        if (comprobante.getEmailReceptor() == null || comprobante.getEmailReceptor().isBlank()) {
            return Mono.empty();
        }
        return Mono.<Void>fromRunnable(() -> sendEmail(comprobante, ridePdf))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void sendEmail(Comprobante comprobante, byte[] ridePdf) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(from);
            helper.setTo(comprobante.getEmailReceptor());
            helper.setSubject(buildSubject(comprobante));
            helper.setText(buildBody(comprobante), true);

            String attachmentName = "factura_" + comprobante.getClaveAcceso() + ".pdf";
            helper.addAttachment(attachmentName, () -> new java.io.ByteArrayInputStream(ridePdf),
                    "application/pdf");

            mailSender.send(message);
            log.info("Factura enviada a {} para comprobante {}", comprobante.getEmailReceptor(), comprobante.getId());
        } catch (Exception e) {
            log.error("Error al enviar factura por email para comprobante {}: {}", comprobante.getId(), e.getMessage());
            throw new RuntimeException("Error al enviar email", e);
        }
    }

    private String buildSubject(Comprobante comprobante) {
        return String.format("Factura Electrónica #%s-%s-%s",
                comprobante.getCodEstablecimiento(),
                comprobante.getCodPuntoEmision(),
                comprobante.getSecuencial());
    }

    private String buildBody(Comprobante comprobante) {
        String total = comprobante.getTotal() != null
                ? String.format("%.2f", comprobante.getTotal()) : "0.00";
        String numAutorizacion = comprobante.getNumeroAutorizacion() != null
                ? comprobante.getNumeroAutorizacion() : "Pendiente";

        return """
                <!DOCTYPE html>
                <html>
                <body style="font-family: Arial, sans-serif; color: #333;">
                  <h2>Factura Electrónica</h2>
                  <p>Estimado/a <strong>%s</strong>,</p>
                  <p>Adjunto encontrará su factura electrónica con los siguientes datos:</p>
                  <table style="border-collapse: collapse; width: 100%%;">
                    <tr>
                      <td style="padding: 8px; border: 1px solid #ddd; background: #f5f5f5;"><strong>Número</strong></td>
                      <td style="padding: 8px; border: 1px solid #ddd;">%s-%s-%s</td>
                    </tr>
                    <tr>
                      <td style="padding: 8px; border: 1px solid #ddd; background: #f5f5f5;"><strong>Importe Total</strong></td>
                      <td style="padding: 8px; border: 1px solid #ddd;">USD %s</td>
                    </tr>
                    <tr>
                      <td style="padding: 8px; border: 1px solid #ddd; background: #f5f5f5;"><strong>Número de Autorización</strong></td>
                      <td style="padding: 8px; border: 1px solid #ddd;">%s</td>
                    </tr>
                  </table>
                  <p style="margin-top: 16px;">El archivo PDF adjunto es su Representación Impresa del Documento Electrónico (RIDE).</p>
                  <hr/>
                  <p style="font-size: 12px; color: #888;">Este es un mensaje automático, por favor no responda a este correo.</p>
                </body>
                </html>
                """.formatted(
                safe(comprobante.getRazonSocialReceptor()),
                safe(comprobante.getCodEstablecimiento()),
                safe(comprobante.getCodPuntoEmision()),
                safe(comprobante.getSecuencial()),
                total,
                numAutorizacion);
    }

    @Override
    public Mono<Void> enviarAlertaVencimientoCertificado(String emailDestino, String rucEmpresa,
                                                          String razonSocial, LocalDate fechaVencimiento) {
        if (!emailEnabled) {
            return Mono.empty();
        }
        if (emailDestino == null || emailDestino.isBlank()) {
            return Mono.empty();
        }
        return Mono.<Void>fromRunnable(() -> sendAlertaCertificado(emailDestino, rucEmpresa, razonSocial, fechaVencimiento))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void sendAlertaCertificado(String emailDestino, String rucEmpresa,
                                        String razonSocial, LocalDate fechaVencimiento) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");

            helper.setFrom(from);
            helper.setTo(emailDestino);
            helper.setSubject("ALERTA: Certificado digital próximo a vencer - " + razonSocial);
            helper.setText(buildAlertaBody(rucEmpresa, razonSocial, fechaVencimiento), false);

            mailSender.send(message);
            log.info("Alerta de vencimiento enviada a {} para empresa {}", emailDestino, rucEmpresa);
        } catch (Exception e) {
            log.error("Error al enviar alerta de vencimiento para empresa {}: {}", rucEmpresa, e.getMessage());
            throw new RuntimeException("Error al enviar alerta de vencimiento", e);
        }
    }

    private String buildAlertaBody(String rucEmpresa, String razonSocial, LocalDate fechaVencimiento) {
        return """
                Estimado usuario,

                El certificado digital de la empresa %s (RUC: %s)
                vencerá el %s.

                Por favor renueve su certificado digital para continuar emitiendo comprobantes electrónicos.

                Atentamente,
                Sistema de Facturación Electrónica
                """.formatted(razonSocial, rucEmpresa, fechaVencimiento);
    }

    private String safe(String value) {
        return value != null ? value : "";
    }
}
