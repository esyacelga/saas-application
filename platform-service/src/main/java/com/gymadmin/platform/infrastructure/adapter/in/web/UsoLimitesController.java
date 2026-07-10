package com.gymadmin.platform.infrastructure.adapter.in.web;

import com.gymadmin.platform.application.service.AccessControlService;
import com.gymadmin.platform.domain.port.in.ConsultarUsoLimitesUseCase;
import com.gymadmin.platform.infrastructure.adapter.in.web.dto.UsoLimitesResponse;
import com.gymadmin.platform.infrastructure.config.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REQ-SAAS-001 (RN-06, HU-04, Sub-fase 1.4): consulta de uso actual vs límites
 * del plan del tenant.
 */
@RestController
@Tag(name = "Uso y límites", description = "Consulta de uso vs cuotas del plan")
public class UsoLimitesController {

    private final ConsultarUsoLimitesUseCase consultarUsoLimitesUseCase;
    private final AccessControlService accessControl;

    public UsoLimitesController(ConsultarUsoLimitesUseCase consultarUsoLimitesUseCase,
                                 AccessControlService accessControl) {
        this.consultarUsoLimitesUseCase = consultarUsoLimitesUseCase;
        this.accessControl = accessControl;
    }

    @Operation(summary = "Uso vs límites del plan activo (owner/admin del tenant)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Uso y cuotas"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "Sin suscripción activa")
    })
    @GetMapping("/api/v1/companias/{id}/uso-limites")
    public Mono<ResponseEntity<UsoLimitesResponse>> consultar(@PathVariable Long id) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireOwnerOrAdminOfCompania(principal, id)
                        .then(consultarUsoLimitesUseCase.consultar(id))
                        .map(result -> ResponseEntity.ok(UsoLimitesResponse.from(result))));
    }

    private Mono<JwtPrincipal> getJwtPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(JwtPrincipal.class);
    }
}
