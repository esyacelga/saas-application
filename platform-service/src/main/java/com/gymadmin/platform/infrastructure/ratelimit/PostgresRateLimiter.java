package com.gymadmin.platform.infrastructure.ratelimit;

import com.gymadmin.platform.domain.exception.RateLimitExcedidoException;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * REQ-SAAS-001 (RN-08): rate limiter simple contra PostgreSQL — evita crear
 * tabla nueva o depender de Redis (ver docs/REDIS_REMOVAL.md).
 * <p>
 * Estrategia: cuenta filas de un bucket contra la propia tabla de dominio en la
 * ventana pedida. Actualmente soporta el bucket
 * {@code pagos_reportados} sobre {@code tenant.pagos_pendientes_validacion}.
 */
@Component
public class PostgresRateLimiter {

    public static final String BUCKET_PAGOS_REPORTADOS = "pagos_reportados";

    private final DatabaseClient databaseClient;

    public PostgresRateLimiter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    /**
     * Verifica que el tenant {@code idCompania} no haya excedido {@code max}
     * operaciones del bucket dentro de la ventana. Emite
     * {@link RateLimitExcedidoException} si ya llegó o superó el límite.
     */
    public Mono<Void> checkRateLimit(String bucket, Long idCompania, int max, Duration ventana) {
        if (idCompania == null) {
            return Mono.empty();
        }
        OffsetDateTime desde = OffsetDateTime.now(ZoneOffset.UTC).minus(ventana);
        return contar(bucket, idCompania, desde)
                .flatMap(actual -> {
                    if (actual >= max) {
                        return Mono.error(new RateLimitExcedidoException(
                                "máx " + max + " " + bucket + " por " + humanize(ventana),
                                humanize(ventana),
                                max));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Long> contar(String bucket, Long idCompania, OffsetDateTime desde) {
        return switch (bucket) {
            case BUCKET_PAGOS_REPORTADOS -> databaseClient.sql(
                            "SELECT COUNT(*) AS cnt FROM tenant.pagos_pendientes_validacion " +
                                    "WHERE id_compania = :id AND fecha_reporte > :desde")
                    .bind("id", idCompania)
                    .bind("desde", desde)
                    .map((row, meta) -> {
                        Number n = row.get("cnt", Number.class);
                        return n == null ? 0L : n.longValue();
                    })
                    .one()
                    .defaultIfEmpty(0L);
            default -> Mono.error(new IllegalArgumentException("bucket desconocido: " + bucket));
        };
    }

    private String humanize(Duration ventana) {
        long horas = ventana.toHours();
        if (horas > 0) {
            return horas + "h";
        }
        long minutos = ventana.toMinutes();
        if (minutos > 0) {
            return minutos + "m";
        }
        return ventana.getSeconds() + "s";
    }
}
