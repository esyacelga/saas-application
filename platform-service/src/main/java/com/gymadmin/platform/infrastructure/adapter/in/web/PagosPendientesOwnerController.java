package com.gymadmin.platform.infrastructure.adapter.in.web;

import com.gymadmin.platform.application.service.AccessControlService;
import com.gymadmin.platform.domain.port.in.ListarPagosPendientesOwnerUseCase;
import com.gymadmin.platform.infrastructure.adapter.in.web.dto.PagoPendienteResponse;
import com.gymadmin.platform.infrastructure.config.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REQ-SAAS-001 (Sub-fase 1.6, item #3): endpoint owner para consultar los pagos
 * pendientes/rechazados de la propia compañía. Alimenta el banner "tu pago está
 * en revisión" o "tu último pago fue rechazado: {motivo}" de la página
 * "Mi suscripción" del frontend admin.
 * <p>
 * El guard multi-tenant se delega en {@link AccessControlService#requireOwnerOrAdminOfCompania}
 * — el path variable {@code {idCompania}} debe coincidir con {@code jwt.id_compania}
 * (super_admin/soporte pueden operar sobre cualquier tenant). Cualquier staff del
 * tenant puede consultarlo; la discriminación por rol es responsabilidad del frontend.
 */
@RestController
@Tag(name = "Pagos pendientes owner", description = "REQ-SAAS-001 Sub-fase 1.6 — pagos del propio tenant")
public class PagosPendientesOwnerController {

    private final ListarPagosPendientesOwnerUseCase listarPagosPendientesOwnerUseCase;
    private final AccessControlService accessControl;

    public PagosPendientesOwnerController(ListarPagosPendientesOwnerUseCase listarPagosPendientesOwnerUseCase,
                                          AccessControlService accessControl) {
        this.listarPagosPendientesOwnerUseCase = listarPagosPendientesOwnerUseCase;
        this.accessControl = accessControl;
    }

    @Operation(
            summary = "Listar pagos pendientes/rechazados del propio tenant",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista ordenada por fecha_reporte DESC"),
            @ApiResponse(responseCode = "403", description = "Tenant mismatch: el JWT no pertenece a la compañía solicitada")
    })
    @GetMapping("/api/v1/companias/{idCompania}/pagos-pendientes")
    public Flux<PagoPendienteResponse> listarDelTenant(
            @PathVariable Long idCompania,
            @RequestParam(defaultValue = "10") int limit) {
        return getJwtPrincipal()
                .flatMapMany(principal -> accessControl.requireOwnerOrAdminOfCompania(principal, idCompania)
                        .thenMany(listarPagosPendientesOwnerUseCase.listarPorCompania(idCompania, limit))
                        .map(PagoPendienteResponse::from));
    }

    private Mono<JwtPrincipal> getJwtPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(JwtPrincipal.class);
    }
}
