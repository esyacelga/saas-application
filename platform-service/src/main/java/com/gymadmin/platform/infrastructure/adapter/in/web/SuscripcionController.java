package com.gymadmin.platform.infrastructure.adapter.in.web;

import com.gymadmin.platform.application.service.AccessControlService;
import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.port.in.ActividadPlataformaUseCase;
import com.gymadmin.platform.domain.port.in.ActivarTrialUseCase;
import com.gymadmin.platform.domain.port.in.CancelarSuscripcionUseCase;
import com.gymadmin.platform.domain.port.in.SuscripcionUseCase;
import com.gymadmin.platform.infrastructure.adapter.in.web.dto.*;
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
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@RestController
@Tag(name = "Suscripciones", description = "Suscripciones activas e historial")
public class SuscripcionController {

    private final SuscripcionUseCase suscripcionUseCase;
    private final AccessControlService accessControl;
    private final ActividadPlataformaUseCase actividadUseCase;
    private final ActivarTrialUseCase activarTrialUseCase;
    private final CancelarSuscripcionUseCase cancelarSuscripcionUseCase;

    public SuscripcionController(SuscripcionUseCase suscripcionUseCase,
                                  AccessControlService accessControl,
                                  ActividadPlataformaUseCase actividadUseCase,
                                  ActivarTrialUseCase activarTrialUseCase,
                                  CancelarSuscripcionUseCase cancelarSuscripcionUseCase) {
        this.suscripcionUseCase = suscripcionUseCase;
        this.accessControl = accessControl;
        this.actividadUseCase = actividadUseCase;
        this.activarTrialUseCase = activarTrialUseCase;
        this.cancelarSuscripcionUseCase = cancelarSuscripcionUseCase;
    }

    @Operation(summary = "Obtener suscripción activa de una compañía", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Suscripción activa"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "Suscripción no encontrada")
    })
    @GetMapping("/api/v1/companias/{id}/suscripcion")
    public Mono<ResponseEntity<SuscripcionResponse>> getSuscripcionActiva(@PathVariable Long id) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireOwnerOrAdminOfCompania(principal, id)
                        .then(suscripcionUseCase.getSuscripcionActiva(id))
                        .map(cp -> ResponseEntity.ok(toResponse(cp))));
    }

    @Operation(summary = "Historial de suscripciones de una compañía", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Historial de suscripciones"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @GetMapping("/api/v1/companias/{id}/suscripcion/historial")
    public Flux<SuscripcionResponse> getHistorial(@PathVariable Long id) {
        return getJwtPrincipal()
                .flatMapMany(principal -> accessControl.requireOwnerOrAdminOfCompania(principal, id)
                        .thenMany(suscripcionUseCase.getHistorial(id).map(this::toResponse)));
    }

    @Operation(summary = "Renovar suscripción de una compañía (super_admin)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Suscripción renovada"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "Suscripción no encontrada")
    })
    @PostMapping("/api/v1/companias/{id}/suscripcion/renovar")
    public Mono<ResponseEntity<SuscripcionResponse>> renovar(@PathVariable Long id,
                                                              @RequestBody(required = false) RenovarRequest request) {
        Long idPlan = request != null ? request.idPlan() : null;
        Integer meses = request != null ? request.meses() : null;
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireSuperAdmin(principal)
                        .then(suscripcionUseCase.renovar(id, new SuscripcionUseCase.RenovarCommand(idPlan, meses)))
                        .flatMap(cp -> actividadUseCase.registrar(new ActividadPlataformaUseCase.RegistrarCommand(
                                "SUSCRIPCION_RENOVADA", "suscripciones", id, null, null, principal.getName()
                        )).onErrorResume(e -> Mono.empty()).thenReturn(cp))
                        .map(cp -> ResponseEntity.ok(toResponse(cp))));
    }

    @Operation(summary = "Upgrade de plan de una compañía (super_admin)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Upgrade aplicado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "Compañía o plan no encontrado")
    })
    @PostMapping("/api/v1/companias/{id}/suscripcion/upgrade")
    public Mono<ResponseEntity<UpgradeResponse>> upgrade(@PathVariable Long id,
                                                          @Valid @RequestBody UpgradeRequest request) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireSuperAdmin(principal)
                        .then(suscripcionUseCase.upgrade(id,
                                new SuscripcionUseCase.UpgradeCommand(request.idPlanNuevo())))
                        .flatMap(result -> actividadUseCase.registrar(new ActividadPlataformaUseCase.RegistrarCommand(
                                "SUSCRIPCION_UPGRADE", "suscripciones", id, null, null, principal.getName()
                        )).onErrorResume(e -> Mono.empty()).thenReturn(result))
                        .map(result -> ResponseEntity.ok(new UpgradeResponse(
                                result.idCompaniaPlanNuevo(),
                                result.creditoAplicado(),
                                result.montoAPagar(),
                                result.planAnteriorCancelado()
                        ))));
    }

    @Operation(summary = "Downgrade de plan de una compañía (super_admin)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Downgrade programado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "Compañía o plan no encontrado")
    })
    @PostMapping("/api/v1/companias/{id}/suscripcion/downgrade")
    public Mono<ResponseEntity<DowngradeResponse>> downgrade(@PathVariable Long id,
                                                              @Valid @RequestBody DowngradeRequest request) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireSuperAdmin(principal)
                        .then(suscripcionUseCase.downgrade(id,
                                new SuscripcionUseCase.DowngradeCommand(request.idPlanNuevo())))
                        .flatMap(result -> actividadUseCase.registrar(new ActividadPlataformaUseCase.RegistrarCommand(
                                "SUSCRIPCION_DOWNGRADE", "suscripciones", id, null, null, principal.getName()
                        )).onErrorResume(e -> Mono.empty()).thenReturn(result))
                        .map(result -> ResponseEntity.ok(new DowngradeResponse(
                                result.idCompaniaPlanNuevo(),
                                result.estado(),
                                result.efectivoDe(),
                                result.creditoGenerado()
                        ))));
    }

    @Operation(summary = "Activar Trial de una compañía (owner/admin del tenant)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Trial activado"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "409", description = "Trial ya usado o suscripción activa")
    })
    @PostMapping("/api/v1/companias/{id}/suscripcion/trial")
    public Mono<ResponseEntity<SuscripcionResponse>> activarTrial(@PathVariable Long id) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireOwnerOrAdminOfCompania(principal, id)
                        .then(activarTrialUseCase.activar(id, toLongOrNull(principal.getUserId())))
                        .flatMap(cp -> actividadUseCase.registrar(new ActividadPlataformaUseCase.RegistrarCommand(
                                "TRIAL_ACTIVADO", "suscripciones", id, null, null, principal.getName()
                        )).onErrorResume(e -> Mono.empty()).thenReturn(cp))
                        .map(cp -> ResponseEntity.ok(toResponse(cp))));
    }

    @Operation(summary = "Cancelar suscripción de una compañía (owner/admin del tenant)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Suscripción cancelada"),
        @ApiResponse(responseCode = "400", description = "No hay suscripción cancelable"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @PostMapping("/api/v1/companias/{id}/suscripcion/cancelar")
    public Mono<ResponseEntity<Void>> cancelarSuscripcion(@PathVariable Long id,
                                                           @RequestBody(required = false) CancelarSuscripcionRequest request) {
        String motivo = request != null ? request.motivo() : null;
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireOwnerOrAdminOfCompania(principal, id)
                        .then(cancelarSuscripcionUseCase.cancelar(id, toLongOrNull(principal.getUserId()), motivo))
                        .then(actividadUseCase.registrar(new ActividadPlataformaUseCase.RegistrarCommand(
                                "SUSCRIPCION_CANCELADA", "suscripciones", id, null, motivo, principal.getName()
                        )).onErrorResume(e -> Mono.empty()))
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

    private SuscripcionResponse toResponse(CompaniaPlan cp) {
        LocalDate today = LocalDate.now();
        long diasRestantes = cp.getFechaFin() != null ? ChronoUnit.DAYS.between(today, cp.getFechaFin()) : 0;
        return new SuscripcionResponse(
                cp.getId(),
                cp.getIdPlan(),
                cp.getEstado() != null ? cp.getEstado().name() : null,
                cp.getFechaInicio(),
                cp.getFechaFin(),
                diasRestantes,
                cp.getDiasGracia(),
                cp.getTipoCambio() != null ? cp.getTipoCambio().name() : null
        );
    }
}
