package com.gymadmin.core.infrastructure.adapter.in.web;

import com.gymadmin.core.application.service.AccessControlService;
import com.gymadmin.core.domain.port.in.MetodoPagoUseCase;
import com.gymadmin.core.infrastructure.adapter.in.web.dto.MetodoPagoResponse;
import com.gymadmin.core.infrastructure.config.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Tag(name = "Métodos de Pago", description = "Catálogo de métodos de pago de la compañía")
@RestController
@RequestMapping("/api/v1/metodos-pago")
public class MetodoPagoController {

    private final MetodoPagoUseCase metodoPagoUseCase;
    private final AccessControlService accessControl;

    public MetodoPagoController(MetodoPagoUseCase metodoPagoUseCase,
                                AccessControlService accessControl) {
        this.metodoPagoUseCase = metodoPagoUseCase;
        this.accessControl = accessControl;
    }

    @Operation(summary = "Listar métodos de pago activos", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200"),
            @ApiResponse(responseCode = "401"),
            @ApiResponse(responseCode = "403")
    })
    @GetMapping
    public Flux<MetodoPagoResponse> listar() {
        return extractPrincipal()
                .flatMapMany(principal -> accessControl.requireGymStaff(principal, principal.getIdCompania())
                        .thenMany(metodoPagoUseCase.listarActivos(principal.getIdCompania())
                                .map(MetodoPagoResponse::from)));
    }

    private Mono<JwtPrincipal> extractPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getPrincipal())
                .cast(JwtPrincipal.class);
    }
}
