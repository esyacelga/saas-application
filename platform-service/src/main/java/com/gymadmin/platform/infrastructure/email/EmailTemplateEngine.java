package com.gymadmin.platform.infrastructure.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REQ-SAAS-001 (Sub-fase 1.5): render simple de templates de email.
 * <p>
 * Motor mínimo con {@code String.replace("{key}", value)}. Los templates viven
 * bajo {@code src/main/resources/email-templates/} en archivos {@code .html} y
 * {@code .txt} paralelos. El subject se deriva del template key (ver
 * {@link #SUBJECTS}).
 * <p>
 * Se cachea el contenido del template tras la primera lectura — los templates
 * son estáticos, se ubican en el classpath, no cambian en runtime.
 */
@Component
public class EmailTemplateEngine {

    private static final Logger log = LoggerFactory.getLogger(EmailTemplateEngine.class);
    private static final String BASE_PATH = "email-templates/";

    /** Subject por templateKey — mantén sincronizado con los archivos {@code .html/.txt}. */
    private static final Map<String, String> SUBJECTS = Map.of(
            "vencimiento_15d", "Tu plan vence en 15 días",
            "vencimiento_7d",  "Tu plan vence en 7 días",
            "vencimiento_3d",  "Tu plan vence en 3 días",
            "vencimiento_1d",  "Tu plan vence mañana",
            "vencimiento_0d",  "Tu plan vence hoy"
    );

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public RenderedEmail render(String templateKey, Map<String, Object> variables) {
        String html = renderResource(templateKey + ".html", variables);
        String text = renderResource(templateKey + ".txt", variables);
        String subject = renderString(SUBJECTS.getOrDefault(templateKey, "Notificación Gym Admin"), variables);
        return new RenderedEmail(subject, html, text);
    }

    private String renderResource(String resourceName, Map<String, Object> variables) {
        String raw = cache.computeIfAbsent(resourceName, this::loadResource);
        return renderString(raw, variables);
    }

    private String loadResource(String resourceName) {
        try {
            ClassPathResource resource = new ClassPathResource(BASE_PATH + resourceName);
            if (!resource.exists()) {
                log.warn("Email template no encontrado: {}", BASE_PATH + resourceName);
                return "";
            }
            try (var in = resource.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar template " + resourceName, e);
        }
    }

    private String renderString(String raw, Map<String, Object> variables) {
        String out = raw;
        if (variables != null) {
            for (Map.Entry<String, Object> e : variables.entrySet()) {
                String placeholder = "{" + e.getKey() + "}";
                String value = e.getValue() != null ? e.getValue().toString() : "";
                out = out.replace(placeholder, value);
            }
        }
        return out;
    }

    public record RenderedEmail(String subject, String html, String text) {}
}
