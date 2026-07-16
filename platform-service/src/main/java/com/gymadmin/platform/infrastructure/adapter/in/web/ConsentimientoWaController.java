package com.gymadmin.platform.infrastructure.adapter.in.web;

import com.gymadmin.platform.application.service.AccessControlService;
import com.gymadmin.platform.domain.model.Compania;
import com.gymadmin.platform.domain.port.in.ConsentimientoWaUseCase;
import com.gymadmin.platform.infrastructure.adapter.in.web.dto.ConsentimientoWaRequest;
import com.gymadmin.platform.infrastructure.adapter.in.web.dto.ConsentimientoWaResponse;
import com.gymadmin.platform.infrastructure.config.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Fase 6 (bloque E) — captura del opt-in de WhatsApp del dueño de la compañía.
 *
 * <p>Acceso: {@code requireAccessToCompania} — permite al operador de plataforma (super_admin) y al
 * <b>admin/dueño de la propia compañía</b> (onboarding/config del dueño, según el diseño). Un staff de
 * otro tenant recibe 403.
 *
 * <p>Semántica: {@code acepta=true} sella {@code fecha_consentimiento_wa} (prueba de opt-in ante Meta);
 * {@code acepta=false} es opt-out y limpia la fecha. Sin este flag el job nunca envía WhatsApp (R4).
 */
@RestController
@Tag(name = "Consentimiento WhatsApp (dueño)", description = "Opt-in de avisos de vencimiento por WhatsApp")
public class ConsentimientoWaController {

    private static final Logger log = LoggerFactory.getLogger(ConsentimientoWaController.class);

    private final ConsentimientoWaUseCase consentimientoUseCase;
    private final AccessControlService accessControl;

    public ConsentimientoWaController(ConsentimientoWaUseCase consentimientoUseCase,
                                      AccessControlService accessControl) {
        this.consentimientoUseCase = consentimientoUseCase;
        this.accessControl = accessControl;
    }

    @Operation(summary = "Registrar opt-in/opt-out de WhatsApp del dueño",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Consentimiento actualizado"),
            @ApiResponse(responseCode = "400", description = "Body inválido (acepta requerido)"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado"),
            @ApiResponse(responseCode = "404", description = "Compañía no encontrada")
    })
    @PatchMapping("/api/v1/companias/{id}/consentimiento-wa")
    public Mono<ConsentimientoWaResponse> actualizar(@PathVariable Long id,
                                                     @Valid @RequestBody ConsentimientoWaRequest request) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireAccessToCompania(principal, id)
                        .then(consentimientoUseCase.actualizarConsentimiento(id, request.acepta()))
                        .map(this::toResponse)
                        .doOnSuccess(r -> log.info(
                                "consentimiento-wa compania={} acepta={} por={}",
                                r.idCompania(), r.aceptaWhatsapp(), principal.getUserId())));
    }

    private Mono<JwtPrincipal> getJwtPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(JwtPrincipal.class);
    }

    private ConsentimientoWaResponse toResponse(Compania c) {
        return new ConsentimientoWaResponse(
                c.getId(),
                c.isAceptaWhatsapp(),
                c.getFechaConsentimientoWa()
        );
    }
}
