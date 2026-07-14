package com.gymadmin.billing.infrastructure.config;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ModuloGatingFilter — gate de facturación consultando platform-service")
class ModuloGatingFilterTest {

    private MockWebServer server;
    private WebClient webClient;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        webClient = WebClient.builder().baseUrl(server.url("/").toString()).build();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private ModuloGatingFilter newFilter(boolean enabled) {
        return new ModuloGatingFilter(webClient, enabled, "FACTURACION", 60L, 10_000L);
    }

    private MockServerWebExchange exchange(String path) {
        MockServerHttpRequest request = MockServerHttpRequest.method(HttpMethod.GET, path).build();
        return MockServerWebExchange.from(request);
    }

    private Mono<Void> runWithPrincipal(ModuloGatingFilter filter,
                                        MockServerWebExchange exchange,
                                        WebFilterChain chain,
                                        JwtPrincipal principal) {
        if (principal == null) {
            return filter.filter(exchange, chain);
        }
        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        return filter.filter(exchange, chain)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
    }

    private JwtPrincipal staffPrincipal(Long idCompania) {
        return new JwtPrincipal("user-1", "staff", "admin_compania", idCompania, 42L, List.of());
    }

    private JwtPrincipal plataformaPrincipal() {
        return new JwtPrincipal("plat-1", "plataforma", "super_admin", null, null, List.of());
    }

    private String readBody(MockServerWebExchange exchange) {
        return exchange.getResponse().getBodyAsString()
                .defaultIfEmpty("")
                .block();
    }

    @Test
    @DisplayName("path fuera de scope: pasa transparente sin consultar platform-service")
    void pathFueraDeScopePasaTransparente() {
        ModuloGatingFilter filter = newFilter(true);
        MockServerWebExchange ex = exchange("/actuator/health");
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = e -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(runWithPrincipal(filter, ex, chain, staffPrincipal(10L)))
                .verifyComplete();

        assertThat(chainCalled).isTrue();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("gating deshabilitado: pasa transparente incluso en rutas protegidas")
    void gatingDeshabilitadoPasaTransparente() {
        ModuloGatingFilter filter = newFilter(false);
        MockServerWebExchange ex = exchange("/api/v1/comprobantes/facturas");
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = e -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(runWithPrincipal(filter, ex, chain, staffPrincipal(10L)))
                .verifyComplete();

        assertThat(chainCalled).isTrue();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("principal plataforma: pasa transparente sin consultar platform-service")
    void principalPlataformaPasaTransparente() {
        ModuloGatingFilter filter = newFilter(true);
        MockServerWebExchange ex = exchange("/api/v1/comprobantes/facturas");
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = e -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(runWithPrincipal(filter, ex, chain, plataformaPrincipal()))
                .verifyComplete();

        assertThat(chainCalled).isTrue();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("cache hit true: pasa sin llamar HTTP")
    void cacheHitPasaSinLlamarHttp() {
        ModuloGatingFilter filter = newFilter(true);
        Long idCompania = 55L;

        // Primera llamada: 200 y cachea true.
        server.enqueue(new MockResponse().setResponseCode(200));

        MockServerWebExchange ex1 = exchange("/api/v1/comprobantes/facturas");
        WebFilterChain chain = e -> Mono.empty();
        StepVerifier.create(runWithPrincipal(filter, ex1, chain, staffPrincipal(idCompania)))
                .verifyComplete();
        assertThat(server.getRequestCount()).isEqualTo(1);

        // Segunda llamada: cache hit, no debe llegar al servidor.
        MockServerWebExchange ex2 = exchange("/api/v1/comprobantes/facturas");
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain2 = e -> {
            chainCalled.set(true);
            return Mono.empty();
        };
        StepVerifier.create(runWithPrincipal(filter, ex2, chain2, staffPrincipal(idCompania)))
                .verifyComplete();
        assertThat(chainCalled).isTrue();
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("HTTP 200: pasa la cadena y cachea true")
    void http200PasaYCachea() {
        ModuloGatingFilter filter = newFilter(true);
        server.enqueue(new MockResponse().setResponseCode(200));

        MockServerWebExchange ex = exchange("/api/v1/comprobantes/facturas");
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain = e -> {
            chainCalled.set(true);
            return Mono.empty();
        };

        StepVerifier.create(runWithPrincipal(filter, ex, chain, staffPrincipal(10L)))
                .verifyComplete();

        assertThat(chainCalled).isTrue();
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("HTTP 403: responde 403 con razon modulo_no_incluido y cachea false")
    void http403RespondeForbiddenYCachea() {
        ModuloGatingFilter filter = newFilter(true);
        server.enqueue(new MockResponse().setResponseCode(403));

        MockServerWebExchange ex = exchange("/api/v1/comprobantes/facturas");
        WebFilterChain chain = e -> Mono.error(new AssertionError("chain no debe invocarse"));

        StepVerifier.create(runWithPrincipal(filter, ex, chain, staffPrincipal(11L)))
                .verifyComplete();

        assertThat(ex.getResponse().getStatusCode().value()).isEqualTo(403);
        String body = readBody(ex);
        assertThat(body).contains("\"permitido\":false");
        assertThat(body).contains("\"razon\":\"modulo_no_incluido\"");

        // Segunda vez: cache hit false, no llega al servidor.
        MockServerWebExchange ex2 = exchange("/api/v1/comprobantes/facturas");
        StepVerifier.create(runWithPrincipal(filter, ex2, chain, staffPrincipal(11L)))
                .verifyComplete();
        assertThat(server.getRequestCount()).isEqualTo(1);
        assertThat(ex2.getResponse().getStatusCode().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("HTTP 402: responde 402 con razon plan_vencido_o_suspendido y no cachea")
    void http402RespondeYNoCachea() {
        ModuloGatingFilter filter = newFilter(true);
        server.enqueue(new MockResponse().setResponseCode(402));
        server.enqueue(new MockResponse().setResponseCode(402));

        WebFilterChain chain = e -> Mono.error(new AssertionError("chain no debe invocarse"));

        MockServerWebExchange ex = exchange("/api/v1/reportes/ventas");
        StepVerifier.create(runWithPrincipal(filter, ex, chain, staffPrincipal(12L)))
                .verifyComplete();
        assertThat(ex.getResponse().getStatusCode().value()).isEqualTo(402);
        assertThat(readBody(ex)).contains("plan_vencido_o_suspendido");

        // Segunda vez: no cachea, así que llama al server otra vez.
        MockServerWebExchange ex2 = exchange("/api/v1/reportes/ventas");
        StepVerifier.create(runWithPrincipal(filter, ex2, chain, staffPrincipal(12L)))
                .verifyComplete();
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("error de red: fail-closed 503 con razon gate_unavailable, no cachea")
    void errorDeRedFailClosed503() {
        ModuloGatingFilter filter = newFilter(true);
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
        server.enqueue(new MockResponse().setResponseCode(200));

        WebFilterChain chain = e -> Mono.empty();

        MockServerWebExchange ex = exchange("/api/v1/anulaciones/1");
        StepVerifier.create(runWithPrincipal(filter, ex, chain, staffPrincipal(13L)))
                .verifyComplete();
        assertThat(ex.getResponse().getStatusCode().value()).isEqualTo(503);
        assertThat(readBody(ex)).contains("gate_unavailable");

        // Segunda vez: no cacheó el fallo; ahora responde 200 y pasa.
        AtomicBoolean chainCalled = new AtomicBoolean(false);
        WebFilterChain chain2 = e -> {
            chainCalled.set(true);
            return Mono.empty();
        };
        MockServerWebExchange ex2 = exchange("/api/v1/anulaciones/1");
        StepVerifier.create(runWithPrincipal(filter, ex2, chain2, staffPrincipal(13L)))
                .verifyComplete();
        assertThat(chainCalled).isTrue();
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("principal staff sin id_compania: deniega 403 id_compania_missing")
    void staffSinIdCompaniaDeniega() {
        ModuloGatingFilter filter = newFilter(true);
        WebFilterChain chain = e -> Mono.error(new AssertionError("chain no debe invocarse"));

        MockServerWebExchange ex = exchange("/api/v1/comprobantes/facturas");
        StepVerifier.create(runWithPrincipal(filter, ex, chain, staffPrincipal(null)))
                .verifyComplete();

        assertThat(ex.getResponse().getStatusCode().value()).isEqualTo(403);
        String body = readBody(ex);
        assertThat(body).contains("id_compania_missing");
        assertThat(server.getRequestCount()).isZero();
    }
}
