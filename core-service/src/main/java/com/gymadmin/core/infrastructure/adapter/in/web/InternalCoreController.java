package com.gymadmin.core.infrastructure.adapter.in.web;

import com.gymadmin.core.infrastructure.adapter.out.http.PlatformServiceClient;
import com.gymadmin.core.infrastructure.exception.ForbiddenException;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REQ-SAAS-001 (Sub-fase 1.4): endpoint interno consumido por platform-service
 * para contar clientes activos de un tenant. Autenticación por header
 * {@code X-Internal-Call} con secreto compartido.
 * <p>
 * Nota: "activos" se define como cualquier cliente cuyo {@code estado} sea
 * distinto de {@code vencido} y que no esté marcado como eliminado.
 * Estados considerados activos: {@code activo}, {@code proximo_vencer},
 * {@code congelado}, {@code riesgo_abandono}.
 */
@Hidden
@RestController
@RequestMapping("/internal/v1")
public class InternalCoreController {

    private final DatabaseClient databaseClient;
    private final String internalSecret;

    public InternalCoreController(DatabaseClient databaseClient,
                                    @Value("${services.internal.secret:${INTERNAL_SECRET:platform-secret-dev}}") String internalSecret) {
        this.databaseClient = databaseClient;
        this.internalSecret = internalSecret;
    }

    @GetMapping("/companias/{id}/clientes-activos/count")
    public Mono<ResponseEntity<Map<String, Object>>> contarClientesActivos(
            @PathVariable("id") Long idCompania,
            @RequestHeader(value = PlatformServiceClient.HEADER_INTERNAL_CALL, required = false) String internalCall) {

        if (internalCall == null || !internalCall.equals(internalSecret)) {
            return Mono.error(new ForbiddenException("Invalid internal call"));
        }

        return databaseClient.sql(
                "SELECT COUNT(*) AS cnt FROM core.clientes " +
                "WHERE id_compania = :id " +
                "  AND eliminado = FALSE " +
                "  AND estado IN ('activo','proximo_vencer','congelado','riesgo_abandono')")
                .bind("id", idCompania)
                .map((row, meta) -> {
                    Number n = row.get("cnt", Number.class);
                    return n == null ? 0L : n.longValue();
                })
                .one()
                .defaultIfEmpty(0L)
                .map(count -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("count", count);
                    return ResponseEntity.ok(body);
                });
    }
}
