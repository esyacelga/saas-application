package com.gymadmin.platform.infrastructure.adapter.in.web;

import com.gymadmin.platform.application.service.AccessControlService;
import com.gymadmin.platform.domain.model.ConfigNotifSuscripcion;
import com.gymadmin.platform.domain.port.in.NotifConfigUseCase;
import com.gymadmin.platform.infrastructure.adapter.in.web.dto.NotifConfigRequest;
import com.gymadmin.platform.infrastructure.adapter.in.web.dto.NotifConfigResponse;
import com.gymadmin.platform.infrastructure.config.JwtPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
public class NotifConfigController {

    private final NotifConfigUseCase notifConfigUseCase;
    private final AccessControlService accessControl;

    public NotifConfigController(NotifConfigUseCase notifConfigUseCase,
                                 AccessControlService accessControl) {
        this.notifConfigUseCase = notifConfigUseCase;
        this.accessControl = accessControl;
    }

    @GetMapping("/api/v1/companias/{id}/notif-config")
    public Flux<NotifConfigResponse> getConfig(@PathVariable Long id) {
        return getJwtPrincipal()
                .flatMapMany(principal -> accessControl.requireSuperAdmin(principal)
                        .thenMany(notifConfigUseCase.getConfig(id).map(this::toResponse)));
    }

    @PutMapping("/api/v1/companias/{id}/notif-config")
    public Mono<ResponseEntity<Void>> updateConfig(@PathVariable Long id,
                                                    @Valid @RequestBody NotifConfigRequest request) {
        List<NotifConfigUseCase.ConfigEntry> entries = request.configs().stream()
                .map(e -> new NotifConfigUseCase.ConfigEntry(e.diasAntes(), e.canal(), e.activo()))
                .toList();
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireSuperAdmin(principal)
                        .then(notifConfigUseCase.updateConfig(id, entries))
                        .thenReturn(ResponseEntity.<Void>noContent().build()));
    }

    private Mono<JwtPrincipal> getJwtPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(JwtPrincipal.class);
    }

    private NotifConfigResponse toResponse(ConfigNotifSuscripcion c) {
        return new NotifConfigResponse(
                c.getIdCompania(),
                c.getDiasAntes(),
                c.getCanal() != null ? c.getCanal().name() : null,
                c.getActivo()
        );
    }
}
