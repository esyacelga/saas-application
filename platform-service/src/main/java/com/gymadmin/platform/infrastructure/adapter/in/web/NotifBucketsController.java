package com.gymadmin.platform.infrastructure.adapter.in.web;

import com.gymadmin.platform.application.service.AccessControlService;
import com.gymadmin.platform.domain.model.NotifBucketGlobal;
import com.gymadmin.platform.domain.port.in.NotifBucketsUseCase;
import com.gymadmin.platform.infrastructure.adapter.in.web.dto.NotifBucketRequest;
import com.gymadmin.platform.infrastructure.adapter.in.web.dto.NotifBucketResponse;
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
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Fase 6 (R1) — política GLOBAL de buckets de aviso previo de vencimiento, editable solo por
 * super_admin. Config de plataforma (no por tenant): tabla {@code saas.notif_buckets_globales}.
 *
 * <p>Solo se edita el aviso <b>previo</b> ({@code diasPrevio}). El aviso del día del vencimiento (0)
 * es fijo, no configurable y no aparece aquí (se expone como {@code diaVencimiento=0} informativo).
 */
@RestController
@Tag(name = "Buckets de Notificación (plataforma)",
        description = "Días de aviso previo de vencimiento, globales, editables por super_admin")
public class NotifBucketsController {

    private static final Logger log = LoggerFactory.getLogger(NotifBucketsController.class);

    /** Aviso del día del vencimiento — fijo, no configurable. Se expone solo como dato informativo. */
    private static final int DIA_VENCIMIENTO_FIJO = 0;

    private final NotifBucketsUseCase notifBucketsUseCase;
    private final AccessControlService accessControl;

    public NotifBucketsController(NotifBucketsUseCase notifBucketsUseCase,
                                  AccessControlService accessControl) {
        this.notifBucketsUseCase = notifBucketsUseCase;
        this.accessControl = accessControl;
    }

    @Operation(summary = "Listar buckets globales de aviso previo (super_admin)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Buckets por destinatario"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @GetMapping("/api/v1/plataforma/notif-buckets")
    public Flux<NotifBucketResponse> listar() {
        return getJwtPrincipal()
                .flatMapMany(principal -> accessControl.requireSuperAdmin(principal)
                        .thenMany(notifBucketsUseCase.listar().map(this::toResponse)));
    }

    @Operation(summary = "Actualizar el bucket de aviso previo de un destinatario (super_admin)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bucket actualizado"),
            @ApiResponse(responseCode = "400", description = "diasPrevio fuera de rango [1,30]"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado"),
            @ApiResponse(responseCode = "404", description = "Destinatario inválido")
    })
    @PutMapping("/api/v1/plataforma/notif-buckets/{destinatario}")
    public Mono<NotifBucketResponse> actualizar(@PathVariable String destinatario,
                                                @Valid @RequestBody NotifBucketRequest request) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireSuperAdmin(principal)
                        .then(notifBucketsUseCase.actualizar(
                                destinatario,
                                request.diasPrevio(),
                                request.activo(),
                                parseUserId(principal.getUserId())))
                        .map(this::toResponse)
                        .doOnSuccess(r -> log.info(
                                "notif-bucket actualizado: destinatario={} diasPrevio={} activo={} por={}",
                                r.destinatario(), r.diasPrevio(), r.activo(), principal.getUserId())));
    }

    private Mono<JwtPrincipal> getJwtPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(JwtPrincipal.class);
    }

    /** {@code modificado_por} es INT FK a saas.usuarios_plataforma; si el userId no es numérico → null. */
    private static Long parseUserId(String userId) {
        try {
            return userId != null ? Long.valueOf(userId) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private NotifBucketResponse toResponse(NotifBucketGlobal b) {
        return new NotifBucketResponse(
                b.getDestinatario().getCodigo(),
                b.getDiasPrevio(),
                b.isActivo(),
                DIA_VENCIMIENTO_FIJO
        );
    }
}
