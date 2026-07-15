package com.gymadmin.core.infrastructure.adapter.in.web;

import com.gymadmin.core.domain.model.ClientePorVencer;
import com.gymadmin.core.domain.model.ClientesPorVencerResult;
import com.gymadmin.core.domain.port.out.ClienteRepository;
import com.gymadmin.core.infrastructure.adapter.out.http.PlatformServiceClient;
import com.gymadmin.core.infrastructure.exception.ForbiddenException;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * REQ-SAAS-001 (Sub-fase 1.4 + Fase 4): endpoints internos consumidos por otros servicios de la
 * plataforma. Autenticación por header {@code X-Internal-Call} con secreto compartido (no JWT).
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

    /** Modos válidos para el filtro de "clientes por vencer" (Fase 4). */
    private static final Set<String> MODOS_VALIDOS = Set.of("calendario", "accesos", "todos");
    private static final int DIAS_MIN = 0;
    private static final int DIAS_MAX = 30;

    private final DatabaseClient databaseClient;
    private final ClienteRepository clienteRepository;
    private final Clock clock;
    private final String internalSecret;

    public InternalCoreController(DatabaseClient databaseClient,
                                    ClienteRepository clienteRepository,
                                    Clock clock,
                                    @Value("${services.internal.secret:${INTERNAL_SECRET:platform-secret-dev}}") String internalSecret) {
        this.databaseClient = databaseClient;
        this.clienteRepository = clienteRepository;
        this.clock = clock;
        this.internalSecret = internalSecret;
    }

    @GetMapping("/companias/{id}/clientes-activos/count")
    public Mono<ResponseEntity<Map<String, Object>>> contarClientesActivos(
            @PathVariable("id") Long idCompania,
            @RequestHeader(value = PlatformServiceClient.HEADER_INTERNAL_CALL, required = false) String internalCall) {

        if (!secretoValido(internalCall)) {
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

    /**
     * REQ-SAAS-001 (Fase 4, C3): socios de una compañía cuya membresía activa está por vencer.
     * Consumido por attendance-service para el envío de avisos por WhatsApp.
     *
     * <p>El {@code telefono} viaja <b>sin</b> normalizar a E.164 (responsabilidad de attendance).
     * La {@code fechaCorte} se resuelve en zona {@code America/Guayaquil} (C4), no se delega al
     * cliente. Excluye {@code congelado} (RN-05) y {@code vencido}.
     *
     * @param dias  umbral de anticipación (calendario: días al vencimiento; accesos: entradas restantes). Rango {@code [0, 30]}.
     * @param modo  {@code calendario} | {@code accesos} | {@code todos} (default {@code todos}).
     */
    @GetMapping("/companias/{id}/clientes-por-vencer")
    public Mono<ResponseEntity<ClientesPorVencerResult>> clientesPorVencer(
            @PathVariable("id") Long idCompania,
            @RequestParam(value = "dias", defaultValue = "3") int dias,
            @RequestParam(value = "modo", defaultValue = "todos") String modo,
            @RequestHeader(value = PlatformServiceClient.HEADER_INTERNAL_CALL, required = false) String internalCall) {

        if (!secretoValido(internalCall)) {
            return Mono.error(new ForbiddenException("Invalid internal call"));
        }
        if (dias < DIAS_MIN || dias > DIAS_MAX) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "dias fuera de rango [" + DIAS_MIN + ", " + DIAS_MAX + "]"));
        }
        if (!MODOS_VALIDOS.contains(modo)) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "modo inválido: " + modo + " (calendario|accesos|todos)"));
        }

        LocalDate fechaCorte = LocalDate.now(clock);
        return clienteRepository.findClientesPorVencer(idCompania, fechaCorte, dias, modo)
                .collectList()
                .map(clientes -> ResponseEntity.ok(
                        new ClientesPorVencerResult(idCompania, fechaCorte, clientes)));
    }

    private boolean secretoValido(String internalCall) {
        return internalCall != null && internalCall.equals(internalSecret);
    }
}
