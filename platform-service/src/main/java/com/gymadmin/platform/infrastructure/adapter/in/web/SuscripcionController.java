package com.gymadmin.platform.infrastructure.adapter.in.web;

import com.gymadmin.platform.application.service.AccessControlService;
import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.port.in.ActividadPlataformaUseCase;
import com.gymadmin.platform.domain.port.in.SuscripcionUseCase;
import com.gymadmin.platform.infrastructure.adapter.in.web.dto.*;
import com.gymadmin.platform.infrastructure.config.JwtPrincipal;
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
public class SuscripcionController {

    private final SuscripcionUseCase suscripcionUseCase;
    private final AccessControlService accessControl;
    private final ActividadPlataformaUseCase actividadUseCase;

    public SuscripcionController(SuscripcionUseCase suscripcionUseCase,
                                  AccessControlService accessControl,
                                  ActividadPlataformaUseCase actividadUseCase) {
        this.suscripcionUseCase = suscripcionUseCase;
        this.accessControl = accessControl;
        this.actividadUseCase = actividadUseCase;
    }

    @GetMapping("/api/v1/companias/{id}/suscripcion")
    public Mono<ResponseEntity<SuscripcionResponse>> getSuscripcionActiva(@PathVariable Long id) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requirePlataforma(principal)
                        .then(suscripcionUseCase.getSuscripcionActiva(id))
                        .map(cp -> ResponseEntity.ok(toResponse(cp))));
    }

    @GetMapping("/api/v1/companias/{id}/suscripcion/historial")
    public Flux<SuscripcionResponse> getHistorial(@PathVariable Long id) {
        return getJwtPrincipal()
                .flatMapMany(principal -> accessControl.requirePlataforma(principal)
                        .thenMany(suscripcionUseCase.getHistorial(id).map(this::toResponse)));
    }

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
