package com.gymadmin.billing.infrastructure.adapter.in.web;

import com.gymadmin.billing.application.command.AprobarAnulacionCommand;
import com.gymadmin.billing.application.command.RechazarAnulacionCommand;
import com.gymadmin.billing.domain.model.EstadoAnulacion;
import com.gymadmin.billing.domain.port.in.AnulacionUseCase;
import com.gymadmin.billing.infrastructure.adapter.in.web.dto.AnulacionResponse;
import com.gymadmin.billing.infrastructure.adapter.in.web.dto.AprobarAnulacionRequest;
import com.gymadmin.billing.infrastructure.adapter.in.web.dto.RechazarAnulacionRequest;
import com.gymadmin.billing.infrastructure.config.JwtPrincipal;
import com.gymadmin.billing.infrastructure.exception.ForbiddenException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Set;

/**
 * G3 · Máquina de estados de anulación fiscal SRI Ecuador.
 * <p>
 * Roles con permiso de resolver (aprobar / rechazar / confirmar-sri):
 * {@code admin_compania}, {@code super_admin}, {@code Dueño}. Los listados y el
 * detalle son visibles a cualquier staff de la misma compañía.
 */
@Tag(name = "Anulaciones", description = "Anulación fiscal SRI (G3): aprobar, rechazar, confirmar en portal, listar")
@RestController
@RequestMapping("/api/v1/anulaciones")
public class AnulacionController {

    private static final Set<String> ROLES_RESOLUCION = Set.of("admin_compania", "super_admin", "Dueño");

    private final AnulacionUseCase anulacionUseCase;

    public AnulacionController(AnulacionUseCase anulacionUseCase) {
        this.anulacionUseCase = anulacionUseCase;
    }

    @Operation(summary = "Aprobar solicitud de anulación",
            description = "Transiciona SOLICITADA → APROBADA. Si la solicitud pidió NC dispara la emisión (G4). Requiere rol admin_compania/super_admin/Dueño.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitud aprobada"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Rol no autorizado"),
            @ApiResponse(responseCode = "404", description = "Solicitud no encontrada"),
            @ApiResponse(responseCode = "422", description = "Estado inválido para la transición")
    })
    @PostMapping("/{id}/aprobar")
    public Mono<ResponseEntity<AnulacionResponse>> aprobar(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) AprobarAnulacionRequest request) {
        return extractPrincipal()
                .flatMap(principal -> requiereRolResolucion(principal)
                        .then(Mono.defer(() -> anulacionUseCase.aprobar(new AprobarAnulacionCommand(
                                id,
                                toIntegerSafe(principal.getIdCompania()),
                                toIntegerSafe(principal.getIdPersona()),
                                request != null ? request.observacion() : null
                        )))))
                .map(a -> ResponseEntity.ok(AnulacionResponse.from(a)));
    }

    @Operation(summary = "Rechazar solicitud de anulación",
            description = "Transiciona SOLICITADA → RECHAZADA. Observación obligatoria. Requiere rol admin_compania/super_admin/Dueño.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitud rechazada"),
            @ApiResponse(responseCode = "400", description = "Falta observación"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Rol no autorizado"),
            @ApiResponse(responseCode = "404", description = "Solicitud no encontrada"),
            @ApiResponse(responseCode = "422", description = "Estado inválido para la transición")
    })
    @PostMapping("/{id}/rechazar")
    public Mono<ResponseEntity<AnulacionResponse>> rechazar(
            @PathVariable Long id,
            @Valid @RequestBody RechazarAnulacionRequest request) {
        return extractPrincipal()
                .flatMap(principal -> requiereRolResolucion(principal)
                        .then(Mono.defer(() -> anulacionUseCase.rechazar(new RechazarAnulacionCommand(
                                id,
                                toIntegerSafe(principal.getIdCompania()),
                                toIntegerSafe(principal.getIdPersona()),
                                request.observacion()
                        )))))
                .map(a -> ResponseEntity.ok(AnulacionResponse.from(a)));
    }

    @Operation(summary = "Confirmar anulación en portal SRI (Flujo A)",
            description = "Transiciona APROBADA → EJECUTADA y marca el comprobante original como ANULADO. Solo Flujo A (sin NC). Requiere rol admin_compania/super_admin/Dueño.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Anulación ejecutada"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "403", description = "Rol no autorizado"),
            @ApiResponse(responseCode = "404", description = "Solicitud no encontrada"),
            @ApiResponse(responseCode = "422", description = "Estado inválido")
    })
    @PostMapping("/{id}/confirmar-sri")
    public Mono<ResponseEntity<AnulacionResponse>> confirmarSri(@PathVariable Long id) {
        return extractPrincipal()
                .flatMap(principal -> requiereRolResolucion(principal)
                        .then(Mono.defer(() -> anulacionUseCase.confirmarSri(
                                id,
                                toIntegerSafe(principal.getIdCompania()),
                                toIntegerSafe(principal.getIdPersona())))))
                .map(a -> ResponseEntity.ok(AnulacionResponse.from(a)));
    }

    @Operation(summary = "Consultar solicitud por ID", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solicitud encontrada"),
            @ApiResponse(responseCode = "401", description = "No autenticado"),
            @ApiResponse(responseCode = "404", description = "No encontrada")
    })
    @GetMapping("/{id}")
    public Mono<ResponseEntity<AnulacionResponse>> buscarPorId(@PathVariable Long id) {
        return extractPrincipal()
                .flatMap(principal -> anulacionUseCase.buscarPorId(id, toIntegerSafe(principal.getIdCompania())))
                .map(a -> ResponseEntity.ok(AnulacionResponse.from(a)));
    }

    @Operation(summary = "Listar solicitudes de anulación con filtros",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista paginada"),
            @ApiResponse(responseCode = "401", description = "No autenticado")
    })
    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> listar(
            @RequestParam(required = false) EstadoAnulacion estado,
            @RequestParam(required = false) Integer idSucursal,
            @RequestParam(required = false) Long idComprobante,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return extractPrincipal()
                .flatMap(principal -> {
                    Integer idCompania = toIntegerSafe(principal.getIdCompania());
                    return anulacionUseCase.contar(idCompania, idSucursal, estado, idComprobante)
                            .flatMap(total -> anulacionUseCase.listar(idCompania, idSucursal, estado, idComprobante, page, limit)
                                    .map(AnulacionResponse::from)
                                    .collectList()
                                    .map(datos -> ResponseEntity.ok(Map.<String, Object>of(
                                            "total", total,
                                            "pagina", page,
                                            "datos", datos
                                    ))));
                });
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Mono<Void> requiereRolResolucion(JwtPrincipal principal) {
        if (!principal.isStaff()) {
            return Mono.error(new ForbiddenException("Solo usuarios staff pueden resolver anulaciones"));
        }
        if (principal.getRolPlataforma() == null
                || !ROLES_RESOLUCION.contains(principal.getRolPlataforma())) {
            return Mono.error(new ForbiddenException(
                    "Rol " + principal.getRolPlataforma() + " no autorizado para resolver anulaciones"));
        }
        return Mono.empty();
    }

    private Mono<JwtPrincipal> extractPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getPrincipal())
                .cast(JwtPrincipal.class);
    }

    private Integer toIntegerSafe(Long value) {
        return value != null ? value.intValue() : null;
    }
}
