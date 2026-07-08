package com.gymadmin.platform.infrastructure.adapter.in.web;

import com.gymadmin.platform.application.service.AccessControlService;
import com.gymadmin.platform.domain.model.PagoSuscripcion;
import com.gymadmin.platform.domain.port.in.PagoUseCase;
import com.gymadmin.platform.infrastructure.adapter.in.web.dto.PagoResponse;
import com.gymadmin.platform.infrastructure.adapter.in.web.dto.RegistrarPagoRequest;
import com.gymadmin.platform.infrastructure.config.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@Tag(name = "Pagos", description = "Registro y confirmación de pagos")
public class PagoController {

    private final PagoUseCase pagoUseCase;
    private final AccessControlService accessControl;

    public PagoController(PagoUseCase pagoUseCase, AccessControlService accessControl) {
        this.pagoUseCase = pagoUseCase;
        this.accessControl = accessControl;
    }

    @Operation(summary = "Historial de pagos de una compañía", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Historial de pagos"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @GetMapping("/api/v1/companias/{id}/pagos")
    public Flux<PagoResponse> getHistorialPagos(@PathVariable Long id) {
        return getJwtPrincipal()
                .flatMapMany(principal -> accessControl.requirePlataforma(principal)
                        .thenMany(pagoUseCase.getHistorialPagos(id).map(this::toResponse)));
    }

    @Operation(summary = "Registrar un pago de suscripción (super_admin o soporte)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Pago registrado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @PostMapping("/api/v1/pagos")
    public Mono<ResponseEntity<PagoResponse>> registrarPago(@Valid @RequestBody RegistrarPagoRequest request) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireSuperAdminOrSoporte(principal)
                        .then(pagoUseCase.registrarPago(new PagoUseCase.RegistrarPagoCommand(
                                request.idCompaniaPlan(),
                                request.monto(),
                                request.metodoPago(),
                                request.tipoPago(),
                                request.referencia(),
                                request.periodoDesde(),
                                request.periodoHasta()
                        )))
                        .map(pago -> ResponseEntity.status(HttpStatus.CREATED).body(toResponse(pago))));
    }

    @Operation(summary = "Confirmar un pago pendiente (super_admin o soporte)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pago confirmado"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "Pago no encontrado")
    })
    @PutMapping("/api/v1/pagos/{id}/confirmar")
    public Mono<ResponseEntity<PagoResponse>> confirmarPago(@PathVariable Long id) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireSuperAdminOrSoporte(principal)
                        .then(pagoUseCase.confirmarPago(id))
                        .map(pago -> ResponseEntity.ok(toResponse(pago))));
    }

    private Mono<JwtPrincipal> getJwtPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(JwtPrincipal.class);
    }

    private PagoResponse toResponse(PagoSuscripcion p) {
        return new PagoResponse(
                p.getId(),
                p.getIdCompaniaPlan(),
                p.getMonto(),
                p.getFechaPago(),
                p.getPeriodoDesde(),
                p.getPeriodoHasta(),
                p.getMetodoPago() != null ? p.getMetodoPago().name() : null,
                p.getTipoPago() != null ? p.getTipoPago().name() : null,
                p.getEstado() != null ? p.getEstado().name() : null,
                p.getReferencia()
        );
    }
}
