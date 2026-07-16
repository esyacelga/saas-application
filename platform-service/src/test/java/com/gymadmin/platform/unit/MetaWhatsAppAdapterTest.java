package com.gymadmin.platform.unit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymadmin.platform.domain.exception.WhatsAppSendException;
import com.gymadmin.platform.infrastructure.adapter.out.whatsapp.MetaWhatsAppAdapter;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("MetaWhatsAppAdapter — envío de plantillas HSM a Meta")
class MetaWhatsAppAdapterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private MetaWhatsAppAdapter adapterConfigurado() {
        return new MetaWhatsAppAdapter(
                WebClient.builder(),
                server.url("/").toString().replaceAll("/$", ""), // base sin la barra final
                "v21.0",
                "PHONE_ID_123",
                "TOKEN_ABC");
    }

    @Test
    @DisplayName("envío OK → request bien formado (type=template, language.code=es, params en orden, + eliminado del 'to')")
    void enviarOk_requestBienFormado() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"messages\":[{\"id\":\"wamid.XYZ\"}]}"));

        StepVerifier.create(adapterConfigurado().enviarPlantilla(
                        "+593987654321", "recordatorio_vencimiento_suscripcion", "es",
                        List.of("Carlos", "Premium", "30/07/2026", "5")))
                .verifyComplete();

        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertEquals("/v21.0/PHONE_ID_123/messages", req.getPath());
        assertEquals("Bearer TOKEN_ABC", req.getHeader("Authorization"));

        JsonNode body = MAPPER.readTree(req.getBody().readUtf8());
        assertEquals("whatsapp", body.get("messaging_product").asText());
        assertEquals("template", body.get("type").asText());
        assertEquals("593987654321", body.get("to").asText(), "Meta exige el 'to' sin '+'");
        assertEquals("recordatorio_vencimiento_suscripcion", body.get("template").get("name").asText());
        assertEquals("es", body.get("template").get("language").get("code").asText());

        JsonNode params = body.get("template").get("components").get(0).get("parameters");
        assertEquals(4, params.size());
        assertEquals("Carlos", params.get(0).get("text").asText());
        assertEquals("Premium", params.get(1).get("text").asText());
        assertEquals("30/07/2026", params.get(2).get("text").asText());
        assertEquals("5", params.get(3).get("text").asText());
    }

    @Test
    @DisplayName("429 → WhatsAppSendException retryable")
    void error429_retryable() {
        server.enqueue(new MockResponse()
                .setResponseCode(429)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"code\":131056,\"message\":\"rate limit\"}}"));

        StepVerifier.create(adapterConfigurado().enviarPlantilla(
                        "+593987654321", "venc_suscripcion_hoy", "es", List.of("Carlos", "Premium")))
                .expectErrorSatisfies(err -> {
                    assertTrue(err instanceof WhatsAppSendException);
                    WhatsAppSendException e = (WhatsAppSendException) err;
                    assertTrue(e.isRetryable(), "429 debe ser retryable");
                    assertEquals(131056, e.getMetaErrorCode());
                })
                .verify();
    }

    @Test
    @DisplayName("400 code=131047 (sin consentimiento) → WhatsAppSendException NO retryable")
    void error400_131047_noRetryable() {
        server.enqueue(new MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"error\":{\"code\":131047,\"message\":\"Re-engagement message\"}}"));

        StepVerifier.create(adapterConfigurado().enviarPlantilla(
                        "+593987654321", "venc_suscripcion_hoy", "es", List.of("Carlos", "Premium")))
                .expectErrorSatisfies(err -> {
                    WhatsAppSendException e = (WhatsAppSendException) err;
                    assertFalse(e.isRetryable(), "400 de negocio NO debe ser retryable");
                    assertEquals(131047, e.getMetaErrorCode());
                })
                .verify();
    }

    @Test
    @DisplayName("5xx → WhatsAppSendException retryable")
    void error5xx_retryable() {
        server.enqueue(new MockResponse().setResponseCode(503));

        StepVerifier.create(adapterConfigurado().enviarPlantilla(
                        "+593987654321", "venc_suscripcion_hoy", "es", List.of("Carlos", "Premium")))
                .expectErrorSatisfies(err ->
                        assertTrue(((WhatsAppSendException) err).isRetryable()))
                .verify();
    }

    @Test
    @DisplayName("sin credenciales → WARN + Mono.empty() (no llama a Meta, el servicio arranca)")
    void sinCredenciales_omiteEnvio() {
        MetaWhatsAppAdapter sinConfig = new MetaWhatsAppAdapter(
                WebClient.builder(),
                server.url("/").toString().replaceAll("/$", ""),
                "v21.0",
                "",   // phone number id vacío
                "");  // token vacío

        StepVerifier.create(sinConfig.enviarPlantilla(
                        "+593987654321", "venc_suscripcion_hoy", "es", List.of("Carlos", "Premium")))
                .verifyComplete();

        assertEquals(0, server.getRequestCount(), "no debe haberse hecho ninguna llamada HTTP");
    }
}
