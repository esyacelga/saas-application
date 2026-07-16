package com.gymadmin.attendance.manual;

import com.gymadmin.attendance.infrastructure.adapter.out.whatsapp.MetaWhatsAppAdapter;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * REQ-SAAS-001 (Fase 5) — <b>envío REAL contra Meta, disparado a mano</b>.
 *
 * <p>NO es un test automatizado: está {@link Disabled} para que nunca corra en {@code mvn test} ni
 * en CI (enviaría un WhatsApp de verdad y gasta cuota). Sirve para validar de punta a punta que
 * las credenciales del {@code .env} + la red + el {@link MetaWhatsAppAdapter} funcionan, usando la
 * plantilla de prueba de Meta {@code hello_world} (idioma {@code en_US}, sin parámetros).
 *
 * <p><b>Cómo ejecutarlo</b> (desde {@code attendance-service/}, con JAVA_HOME = Zulu 25):
 * <pre>
 *   # 1. En el .env: META_PHONE_NUMBER_ID, META_ACCESS_TOKEN, META_API_VERSION ya configurados.
 *   # 2. Fija el número destino (con o sin '+', formato E.164):
 *   $env:WA_TEST_DESTINO = "+593979151957"
 *   # 3. Quita el @Disabled de esta clase (coméntalo) y corre solo este test:
 *   mvn test -Dtest=EnvioRealWhatsAppManualTest
 * </pre>
 *
 * <p>Si el número emisor está en modo sandbox, el destino debe estar agregado como número de
 * prueba en el panel de Meta, o Meta rechaza el envío.
 */
@Disabled("Envío REAL a Meta — quitar este @Disabled manualmente para dispararlo. No debe correr en CI.")
@DisplayName("MANUAL — envío real de hello_world a Meta (valida credenciales de punta a punta)")
class EnvioRealWhatsAppManualTest {

    @Test
    @DisplayName("envía hello_world (en_US) al número WA_TEST_DESTINO y no lanza error")
    void enviarHelloWorldReal() {
        Dotenv env = Dotenv.configure().ignoreIfMissing().load();

        String baseUrl = valor(env, "META_API_BASE_URL", "https://graph.facebook.com");
        String version = valor(env, "META_API_VERSION", "v21.0");
        String phoneId = valor(env, "META_PHONE_NUMBER_ID", "");
        String token = valor(env, "META_ACCESS_TOKEN", "");
        String destino = valor(env, "WA_TEST_DESTINO", "");

        assertFalse(phoneId.isBlank(), "META_PHONE_NUMBER_ID vacío — configúralo en el .env");
        assertFalse(token.isBlank(), "META_ACCESS_TOKEN vacío — configúralo en el .env");
        assertFalse(destino.isBlank(),
                "WA_TEST_DESTINO vacío — fíjalo, ej: $env:WA_TEST_DESTINO = \"+593979151957\"");

        MetaWhatsAppAdapter adapter = new MetaWhatsAppAdapter(
                WebClient.builder(), baseUrl, version, phoneId, token);

        System.out.println(">> Enviando hello_world a " + destino + " vía " + baseUrl + "/" + version);

        // hello_world no tiene parámetros -> lista vacía. Idioma en_US (plantilla demo de Meta).
        adapter.enviarPlantilla(destino, "hello_world", "en_US", List.of())
                .doOnError(e -> System.out.println(">> ERROR: " + e.getMessage()))
                .doOnSuccess(v -> System.out.println(">> OK — Meta aceptó el envío (revisa tu WhatsApp)"))
                .block();
    }

    /** Lee del .env, con fallback a variable de entorno del sistema y luego al default. */
    private static String valor(Dotenv env, String clave, String porDefecto) {
        String v = env.get(clave);
        if (v == null || v.isBlank()) {
            v = System.getenv(clave);
        }
        return (v == null || v.isBlank()) ? porDefecto : v;
    }
}
