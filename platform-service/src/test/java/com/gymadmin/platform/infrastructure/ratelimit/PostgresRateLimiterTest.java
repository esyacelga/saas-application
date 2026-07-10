package com.gymadmin.platform.infrastructure.ratelimit;

import com.gymadmin.platform.domain.exception.RateLimitExcedidoException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.r2dbc.core.DatabaseClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * REQ-SAAS-001 (RN-08): tests unitarios del rate limiter. Simulan 4 llamadas
 * consecutivas incrementando el conteo — la 4a debe fallar con
 * {@link RateLimitExcedidoException}.
 */
class PostgresRateLimiterTest {

    private DatabaseClient databaseClient;
    private DatabaseClient.GenericExecuteSpec spec;
    private PostgresRateLimiter rateLimiter;
    private AtomicLong contador;

    @BeforeEach
    void setUp() {
        databaseClient = mock(DatabaseClient.class);
        spec = mock(DatabaseClient.GenericExecuteSpec.class);
        contador = new AtomicLong(0);

        when(databaseClient.sql(anyString())).thenReturn(spec);
        when(spec.bind(anyString(), any())).thenReturn(spec);

        org.springframework.r2dbc.core.RowsFetchSpec<Long> fetchSpec = mock(org.springframework.r2dbc.core.RowsFetchSpec.class);
        when(spec.map(any(java.util.function.BiFunction.class))).thenReturn(fetchSpec);
        when(fetchSpec.one()).thenAnswer(inv -> Mono.just(contador.get()));

        rateLimiter = new PostgresRateLimiter(databaseClient);
    }

    @Test
    void permitePrimerasTresLlamadas() {
        for (int i = 0; i < 3; i++) {
            StepVerifier.create(rateLimiter.checkRateLimit(
                            PostgresRateLimiter.BUCKET_PAGOS_REPORTADOS,
                            42L,
                            3,
                            Duration.ofHours(1)))
                    .verifyComplete();
            contador.incrementAndGet();
        }
    }

    @Test
    void cuartaLlamadaFallaConRateLimitExcedido() {
        contador.set(3);
        StepVerifier.create(rateLimiter.checkRateLimit(
                        PostgresRateLimiter.BUCKET_PAGOS_REPORTADOS,
                        42L,
                        3,
                        Duration.ofHours(1)))
                .expectErrorSatisfies(err -> {
                    if (!(err instanceof RateLimitExcedidoException rle)) {
                        throw new AssertionError("esperaba RateLimitExcedidoException, obtuve " + err.getClass());
                    }
                    if (!"1h".equals(rle.getVentana())) {
                        throw new AssertionError("esperaba ventana 1h, obtuve " + rle.getVentana());
                    }
                    if (rle.getMax() != 3) {
                        throw new AssertionError("esperaba max 3, obtuve " + rle.getMax());
                    }
                })
                .verify();
    }

    @Test
    void idCompaniaNullNoAplicaLimite() {
        StepVerifier.create(rateLimiter.checkRateLimit(
                        PostgresRateLimiter.BUCKET_PAGOS_REPORTADOS,
                        null,
                        3,
                        Duration.ofHours(1)))
                .verifyComplete();
    }

    @Test
    void bucketDesconocidoLanzaIllegalArgument() {
        StepVerifier.create(rateLimiter.checkRateLimit(
                        "bucket_inexistente",
                        42L,
                        3,
                        Duration.ofHours(1)))
                .expectError(IllegalArgumentException.class)
                .verify();
    }
}
