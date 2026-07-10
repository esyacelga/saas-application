package com.gymadmin.platform.infrastructure.adapter.in.web;

import com.gymadmin.platform.application.service.AccessControlService;
import com.gymadmin.platform.domain.port.in.BannerUseCase;
import com.gymadmin.platform.domain.port.in.BannerUseCase.BannerActivoView;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REQ-SAAS-001 (Sub-fase 1.5): banners in-app de vencimiento — vista y descarte
 * por el owner/admin del tenant.
 */
@RestController
@RequestMapping("/api/v1/companias/{id}")
@Tag(name = "Banners in-app", description = "REQ-SAAS-001 Sub-fase 1.5 — banners de vencimiento")
public class BannerController {

    private final BannerUseCase bannerUseCase;
    private final AccessControlService accessControl;

    public BannerController(BannerUseCase bannerUseCase,
                             AccessControlService accessControl) {
        this.bannerUseCase = bannerUseCase;
        this.accessControl = accessControl;
    }

    @Operation(
            summary = "Listar banners in-app activos del tenant",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Banners activos"),
            @ApiResponse(responseCode = "403", description = "Tenant mismatch")
    })
    @GetMapping("/banners-activos")
    public Flux<BannerActivoView> listarActivos(@PathVariable("id") Long idCompania) {
        return getJwtPrincipal()
                .flatMapMany(principal -> accessControl.requireOwnerOrAdminOfCompania(principal, idCompania)
                        .thenMany(bannerUseCase.listarBannersActivos(idCompania)));
    }

    @Operation(
            summary = "Descartar un banner",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Banner descartado"),
            @ApiResponse(responseCode = "403", description = "Tenant mismatch"),
            @ApiResponse(responseCode = "404", description = "Banner no encontrado para este tenant")
    })
    @PostMapping("/banners/{idBanner}/descartar")
    public Mono<ResponseEntity<Void>> descartar(@PathVariable("id") Long idCompania,
                                                  @PathVariable("idBanner") Long idBanner) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireOwnerOrAdminOfCompania(principal, idCompania)
                        .then(Mono.defer(() -> bannerUseCase.descartarBanner(idBanner, idCompania)))
                        .map(ok -> Boolean.TRUE.equals(ok)
                                ? ResponseEntity.noContent().<Void>build()
                                : ResponseEntity.notFound().<Void>build()));
    }

    private Mono<JwtPrincipal> getJwtPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(JwtPrincipal.class);
    }
}
