package com.gymadmin.attendance.unit;

import com.gymadmin.attendance.infrastructure.adapter.out.platform.PlatformServiceClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Fase 6 (R1): {@link PlatformServiceClient} lee el bucket previo del socio del endpoint interno de
 * platform, con header {@code X-Internal-Call} y fallback tolerante a fallos.
 */
@DisplayName("PlatformServiceClient — bucket previo del socio + fallback (Fase 6)")
class PlatformServiceClientTest {

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

    private PlatformServiceClient clientConSecreto(String secreto) {
        WebClient wc = WebClient.builder()
                .baseUrl(server.url("/").toString().replaceAll("/$", ""))
                .build();
        PlatformServiceClient client = new PlatformServiceClient(wc);
        ReflectionTestUtils.setField(client, "internalSecret", secreto);
        return client;
    }

    @Test
    @DisplayName("activo=true → devuelve dias_previo de la respuesta; envía X-Internal-Call")
    void activo_devuelveDiasPrevio() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"destinatario\":\"socio\",\"dias_previo\":7,\"activo\":true}"));

        StepVerifier.create(clientConSecreto("s3cr3t").obtenerBucketPrevioSocio(3))
                .expectNext(7)
                .verifyComplete();

        RecordedRequest req = server.takeRequest();
        assertEquals("/internal/v1/notif-buckets/socio", req.getPath());
        assertEquals("s3cr3t", req.getHeader("X-Internal-Call"));
    }

    @Test
    @DisplayName("activo=false → bucket previo desactivado (0)")
    void inactivo_devuelveCero() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"destinatario\":\"socio\",\"dias_previo\":5,\"activo\":false}"));

        StepVerifier.create(clientConSecreto("s3cr3t").obtenerBucketPrevioSocio(3))
                .expectNext(0)
                .verifyComplete();
    }

    @Test
    @DisplayName("404 (fila ausente) → fallback recibido")
    void notFound_devuelveFallback() {
        server.enqueue(new MockResponse().setResponseCode(404));

        StepVerifier.create(clientConSecreto("s3cr3t").obtenerBucketPrevioSocio(3))
                .expectNext(3)
                .verifyComplete();
    }

    @Test
    @DisplayName("5xx / platform caído → fallback recibido (el job nunca se rompe)")
    void error5xx_devuelveFallback() {
        server.enqueue(new MockResponse().setResponseCode(503));

        StepVerifier.create(clientConSecreto("s3cr3t").obtenerBucketPrevioSocio(4))
                .expectNext(4)
                .verifyComplete();
    }
}
