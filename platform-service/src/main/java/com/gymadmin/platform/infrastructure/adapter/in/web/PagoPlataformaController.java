package com.gymadmin.platform.infrastructure.adapter.in.web;

import com.gymadmin.platform.application.service.AccessControlService;
import com.gymadmin.platform.domain.port.in.AprobarPagoUseCase;
import com.gymadmin.platform.domain.port.in.ListarPagosPendientesUseCase;
import com.gymadmin.platform.domain.port.in.RechazarPagoUseCase;
import com.gymadmin.platform.infrastructure.adapter.in.web.dto.PagoPendienteResponse;
import com.gymadmin.platform.infrastructure.adapter.in.web.dto.RechazarPagoRequest;
import com.gymadmin.platform.infrastructure.config.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REQ-SAAS-001 (RN-08, HU-05, Sub-fase 1.4): bandeja de pagos pendientes de
 * validación para operadores root/soporte de la plataforma.
 */
@RestController
@RequestMapping("/api/v1/plataforma/pagos-pendientes")
@Tag(name = "Pagos plataforma", description = "Bandeja de pagos pendientes root/soporte")
public class PagoPlataformaController {

    private final ListarPagosPendientesUseCase listarPagosPendientesUseCase;
    private final AprobarPagoUseCase aprobarPagoUseCase;
    private final RechazarPagoUseCase rechazarPagoUseCase;
    private final AccessControlService accessControl;

    public PagoPlataformaController(ListarPagosPendientesUseCase listarPagosPendientesUseCase,
                                     AprobarPagoUseCase aprobarPagoUseCase,
                                     RechazarPagoUseCase rechazarPagoUseCase,
                                     AccessControlService accessControl) {
        this.listarPagosPendientesUseCase = listarPagosPendientesUseCase;
        this.aprobarPagoUseCase = aprobarPagoUseCase;
        this.rechazarPagoUseCase = rechazarPagoUseCase;
        this.accessControl = accessControl;
    }

    @Operation(summary = "Listar pagos pendientes de validación (root/soporte)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Bandeja paginada"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @GetMapping
    public Mono<ResponseEntity<Map<String, Object>>> listar(
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "1") int pagina,
            @RequestParam(defaultValue = "20") int limit) {
        ListarPagosPendientesUseCase.ListarQuery query =
                new ListarPagosPendientesUseCase.ListarQuery(estado, pagina, limit);
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireRootOrSoporte(principal)
                        .then(listarPagosPendientesUseCase.contar(query))
                        .flatMap(total -> listarPagosPendientesUseCase.listar(query)
                                .map(PagoPendienteResponse::from)
                                .collectList()
                                .map(datos -> {
                                    Map<String, Object> body = new LinkedHashMap<>();
                                    body.put("total", total);
                                    body.put("pagina", query.pagina());
                                    body.put("limit", query.limit());
                                    body.put("datos", datos);
                                    return ResponseEntity.ok(body);
                                })));
    }

    @Operation(summary = "Aprobar un pago pendiente (root/soporte)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pago aprobado"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "Pago no encontrado"),
        @ApiResponse(responseCode = "409", description = "Pago ya procesado")
    })
    @PostMapping("/{id}/aprobar")
    public Mono<ResponseEntity<Map<String, Object>>> aprobar(@PathVariable Long id) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireRootOrSoporte(principal)
                        .then(aprobarPagoUseCase.aprobar(id, toLongOrNull(principal.getUserId())))
                        .map(cp -> {
                            Map<String, Object> body = new LinkedHashMap<>();
                            body.put("id_pago", id);
                            body.put("id_compania_plan", cp.getId());
                            body.put("estado", cp.getEstado() != null ? cp.getEstado().name() : null);
                            return ResponseEntity.ok(body);
                        }));
    }

    @Operation(summary = "Rechazar un pago pendiente (root/soporte)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Pago rechazado"),
        @ApiResponse(responseCode = "400", description = "Motivo inválido"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "Pago no encontrado"),
        @ApiResponse(responseCode = "409", description = "Pago ya procesado")
    })
    @PostMapping("/{id}/rechazar")
    public Mono<ResponseEntity<Void>> rechazar(@PathVariable Long id,
                                                @Valid @RequestBody RechazarPagoRequest request) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireRootOrSoporte(principal)
                        .then(rechazarPagoUseCase.rechazar(id, toLongOrNull(principal.getUserId()), request.motivo()))
                        .thenReturn(ResponseEntity.<Void>noContent().build()));
    }

    private Long toLongOrNull(String userId) {
        if (userId == null || userId.isBlank()) return null;
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Mono<JwtPrincipal> getJwtPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(JwtPrincipal.class);
    }
}
