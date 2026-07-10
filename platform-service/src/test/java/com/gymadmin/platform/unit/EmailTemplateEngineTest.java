package com.gymadmin.platform.unit;

import com.gymadmin.platform.infrastructure.email.EmailTemplateEngine;
import com.gymadmin.platform.infrastructure.email.EmailTemplateEngine.RenderedEmail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-SAAS-001 (Sub-fase 1.5): verifica el reemplazo simple de variables en el
 * motor de templates.
 */
@DisplayName("EmailTemplateEngine — reemplazo de variables")
class EmailTemplateEngineTest {

    private final EmailTemplateEngine engine = new EmailTemplateEngine();

    @Test
    @DisplayName("vencimiento_15d — reemplaza owner_nombre, plan_actual y dias_restantes en HTML y TXT")
    void reemplazaVariablesEnVencimiento15d() {
        RenderedEmail rendered = engine.render("vencimiento_15d", Map.of(
                "owner_nombre", "Juan Pérez",
                "plan_actual", "Trial",
                "plan_destino", "Free",
                "dias_restantes", 15,
                "fecha_vencimiento", "2026-07-25",
                "url_comprar_premium", "https://x.test/planes",
                "url_gym_admin", "https://x.test"
        ));

        assertThat(rendered.subject()).isEqualTo("Tu plan vence en 15 días");
        assertThat(rendered.html())
                .contains("Juan Pérez")
                .contains("Trial")
                .contains("Free")
                .contains("15")
                .contains("https://x.test/planes")
                .doesNotContain("{owner_nombre}")
                .doesNotContain("{plan_actual}");
        assertThat(rendered.text())
                .contains("Juan Pérez")
                .contains("Trial")
                .contains("https://x.test/planes")
                .doesNotContain("{owner_nombre}");
    }

    @Test
    @DisplayName("template inexistente — retorna cuerpo vacío sin lanzar")
    void templateInexistenteRetornaVacio() {
        RenderedEmail rendered = engine.render("no_existe", Map.of());
        assertThat(rendered.html()).isEmpty();
        assertThat(rendered.text()).isEmpty();
        assertThat(rendered.subject()).isEqualTo("Notificación Gym Admin");
    }

    @Test
    @DisplayName("variables null — reemplaza por cadena vacía")
    void variablesNullSeReemplazanPorVacio() {
        var vars = new java.util.HashMap<String, Object>();
        vars.put("owner_nombre", null);
        vars.put("plan_actual", "Premium");
        vars.put("plan_destino", "Free");
        vars.put("dias_restantes", 1);
        vars.put("fecha_vencimiento", "");
        vars.put("url_comprar_premium", "");
        vars.put("url_gym_admin", "");

        RenderedEmail rendered = engine.render("vencimiento_1d", vars);

        assertThat(rendered.html()).contains("Premium").doesNotContain("{owner_nombre}");
    }
}
