package com.gymadmin.core.unit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.gymadmin.core.infrastructure.config.ApiAccessDeniedHandler;
import com.gymadmin.core.infrastructure.config.ApiAuthenticationEntryPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica que los 401/403 de Spring Security emitan el sobre estándar
 * (hallazgo #1 del contrato): sin esto, el error más común (token ausente/
 * expirado) escaparía al contrato.
 */
@DisplayName("Security — 401/403 emiten el sobre estandarizado")
class SecurityErrorContractTest {

    private ObjectMapper objectMapper;
    private ApiAuthenticationEntryPoint entryPoint;
    private ApiAccessDeniedHandler accessDeniedHandler;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        entryPoint = new ApiAuthenticationEntryPoint(objectMapper);
        accessDeniedHandler = new ApiAccessDeniedHandler(objectMapper);
    }

    @Test
    @DisplayName("401 → codigo=no_autenticado con content-type problem+json")
    void unauthorized() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/clientes").build());
        StepVerifier.create(entryPoint.commence(exchange, new BadCredentialsException("no token")))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        JsonNode json = parse(exchange);
        assertThat(json.get("status").asInt()).isEqualTo(401);
        assertThat(json.get("codigo").asText()).isEqualTo("no_autenticado");
        assertThat(json.hasNonNull("mensaje")).isTrue();
    }

    @Test
    @DisplayName("403 → codigo=acceso_denegado con content-type problem+json")
    void forbidden() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/clientes").build());
        StepVerifier.create(accessDeniedHandler.handle(exchange, new AccessDeniedException("denied")))
                .verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        JsonNode json = parse(exchange);
        assertThat(json.get("status").asInt()).isEqualTo(403);
        assertThat(json.get("codigo").asText()).isEqualTo("acceso_denegado");
    }

    private JsonNode parse(MockServerWebExchange exchange) {
        byte[] bytes = DataBufferUtils.join(exchange.getResponse().getBody())
                .map(buf -> {
                    byte[] b = new byte[buf.readableByteCount()];
                    buf.read(b);
                    DataBufferUtils.release(buf);
                    return b;
                })
                .block();
        try {
            return objectMapper.readTree(bytes != null ? bytes : new byte[]{'{', '}'});
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
