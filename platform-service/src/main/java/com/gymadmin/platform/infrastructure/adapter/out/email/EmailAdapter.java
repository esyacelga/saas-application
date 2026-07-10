package com.gymadmin.platform.infrastructure.adapter.out.email;

import com.gymadmin.platform.domain.port.out.EmailSender;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;

/**
 * REQ-SAAS-001 (Sub-fase 1.5): adaptador SMTP para el {@link EmailSender}.
 * <p>
 * Copiado de {@code auth-service/EmailAdapter}, adaptado al paquete de este
 * servicio. Si {@code spring.mail.username} está vacío el adapter loguea WARN
 * y devuelve {@code Mono.empty()} — útil en dev/CI cuando no hay SMTP.
 */
@Component
public class EmailAdapter implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailAdapter.class);

    private final JavaMailSender mailSender;
    private final String from;

    public EmailAdapter(JavaMailSender mailSender,
                        @Value("${spring.mail.username:}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    @Override
    public Mono<Void> enviar(String to, String subject, String htmlBody, String textBody) {
        if (from == null || from.isBlank()) {
            log.warn("SMTP no configurado (spring.mail.username vacío) — se omite envío a {}: '{}'", to, subject);
            return Mono.empty();
        }
        return Mono.fromCallable(() -> {
                    MimeMessage message = mailSender.createMimeMessage();
                    MimeMessageHelper helper = new MimeMessageHelper(
                            message, true, StandardCharsets.UTF_8.name());
                    helper.setFrom(from, "Gym Admin");
                    helper.setTo(to);
                    helper.setSubject(subject);
                    // multipart/alternative: primero texto plano, después HTML.
                    helper.setText(textBody != null ? textBody : "", htmlBody != null ? htmlBody : "");
                    mailSender.send(message);
                    return null;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(MessagingException.class,
                        e -> new RuntimeException("Error al enviar email de notificación", e))
                .then();
    }
}
