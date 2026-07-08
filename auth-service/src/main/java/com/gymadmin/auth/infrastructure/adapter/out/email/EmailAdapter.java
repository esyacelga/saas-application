package com.gymadmin.auth.infrastructure.adapter.out.email;

import com.gymadmin.auth.domain.port.out.EmailPort;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class EmailAdapter implements EmailPort {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String from;

    @Override
    public Mono<Void> sendPasswordResetEmail(String to, String nombre, String resetLink) {
        return Mono.fromCallable(() -> {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(from, "Gym Admin");
            helper.setTo(to);
            helper.setSubject("Restablece tu contraseña - Gym Admin");
            helper.setText(buildHtml(nombre, resetLink), true);
            mailSender.send(message);
            return null;
        })
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorMap(MessagingException.class, e -> new RuntimeException("Error al enviar email de recuperación", e))
        .then();
    }

    private String buildHtml(String nombre, String resetLink) {
        return """
                <!DOCTYPE html>
                <html lang="es">
                <head><meta charset="UTF-8"></head>
                <body style="font-family:Arial,sans-serif;color:#333;max-width:600px;margin:auto;padding:24px">
                  <h2 style="color:#1a73e8">Restablecimiento de contraseña</h2>
                  <p>Hola <strong>%s</strong>,</p>
                  <p>Recibimos una solicitud para restablecer la contraseña de tu cuenta en Gym Admin.</p>
                  <p style="margin:32px 0">
                    <a href="%s"
                       style="background:#1a73e8;color:#fff;padding:12px 24px;border-radius:4px;
                              text-decoration:none;font-weight:bold">
                      Restablecer contraseña
                    </a>
                  </p>
                  <p>Este enlace expira en <strong>1 hora</strong>.</p>
                  <p style="color:#888;font-size:13px">
                    Si no solicitaste esto, puedes ignorar este correo.
                    Tu contraseña no cambiará.
                  </p>
                </body>
                </html>
                """.formatted(nombre, resetLink);
    }
}
