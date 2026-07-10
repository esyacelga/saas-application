package com.gymadmin.platform.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gymadmin.platform.domain.exception.EstadoInvalidoException;
import com.gymadmin.platform.domain.exception.LimiteAlcanzadoException;
import com.gymadmin.platform.domain.exception.PagoDuplicadoException;
import com.gymadmin.platform.domain.exception.PagoYaProcesadoException;
import com.gymadmin.platform.domain.exception.RateLimitExcedidoException;
import com.gymadmin.platform.domain.exception.SinSuscripcionCancelableException;
import com.gymadmin.platform.domain.exception.SuscripcionActivaException;
import com.gymadmin.platform.domain.exception.TrialYaUsadoException;
import com.gymadmin.platform.domain.model.RecursoLimitable;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * REQ-SAAS-001 (Sub-fase 1.4): valida que cada excepción de dominio Freemium se
 * mapee al HTTP status correcto en {@link GlobalExceptionHandler}.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new ObjectMapper());

    @Test
    void limiteAlcanzadoLanzaHttp403() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/companias/1/sucursales"));
        StepVerifier.create(handler.handle(exchange,
                        new LimiteAlcanzadoException(RecursoLimitable.SUCURSALES, 5, 5, "FREE")))
                .verifyComplete();
        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
    }

    @Test
    void trialYaUsadoLanzaHttp409() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/companias/1/suscripcion/trial"));
        StepVerifier.create(handler.handle(exchange, new TrialYaUsadoException("Ya usó su trial")))
                .verifyComplete();
        assertEquals(HttpStatus.CONFLICT, exchange.getResponse().getStatusCode());
    }

    @Test
    void suscripcionActivaLanzaHttp409() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/companias/1/suscripcion/trial"));
        StepVerifier.create(handler.handle(exchange, new SuscripcionActivaException("Tiene suscripción activa")))
                .verifyComplete();
        assertEquals(HttpStatus.CONFLICT, exchange.getResponse().getStatusCode());
    }

    @Test
    void sinSuscripcionCancelableLanzaHttp400() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/companias/1/suscripcion/cancelar"));
        StepVerifier.create(handler.handle(exchange, new SinSuscripcionCancelableException("No hay sub cancelable")))
                .verifyComplete();
        assertEquals(HttpStatus.BAD_REQUEST, exchange.getResponse().getStatusCode());
    }

    @Test
    void pagoDuplicadoLanzaHttp409() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/companias/1/pagos/reportar"));
        StepVerifier.create(handler.handle(exchange, new PagoDuplicadoException("Pago duplicado", "abc")))
                .verifyComplete();
        assertEquals(HttpStatus.CONFLICT, exchange.getResponse().getStatusCode());
    }

    @Test
    void pagoYaProcesadoLanzaHttp409() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/plataforma/pagos-pendientes/7/aprobar"));
        StepVerifier.create(handler.handle(exchange, new PagoYaProcesadoException("Ya procesado", 7L)))
                .verifyComplete();
        assertEquals(HttpStatus.CONFLICT, exchange.getResponse().getStatusCode());
    }

    @Test
    void estadoInvalidoLanzaHttp400() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.put("/api/v1/companias/1"));
        StepVerifier.create(handler.handle(exchange, new EstadoInvalidoException("Transición no permitida")))
                .verifyComplete();
        assertEquals(HttpStatus.BAD_REQUEST, exchange.getResponse().getStatusCode());
    }

    @Test
    void rateLimitLanzaHttp429() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/api/v1/companias/1/pagos/reportar"));
        StepVerifier.create(handler.handle(exchange,
                        new RateLimitExcedidoException("máx 3 en 1h", "1h", 3)))
                .verifyComplete();
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.getResponse().getStatusCode());
    }
}
