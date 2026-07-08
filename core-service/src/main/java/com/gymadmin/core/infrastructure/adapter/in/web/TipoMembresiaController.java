package com.gymadmin.core.infrastructure.adapter.in.web;

import com.gymadmin.core.application.service.AccessControlService;
import com.gymadmin.core.domain.model.TipoMembresia;
import com.gymadmin.core.domain.port.in.TipoMembresiaUseCase;
import com.gymadmin.core.infrastructure.adapter.in.web.dto.TipoMembresiaRequest;
import com.gymadmin.core.infrastructure.adapter.in.web.dto.TipoMembresiaResponse;
import com.gymadmin.core.infrastructure.config.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Tag(name = "Tipos de Membresía", description = "Catálogo de tipos de membresía")
@RestController
@RequestMapping("/api/v1/tipos-membresia")
public class TipoMembresiaController {

    private final TipoMembresiaUseCase tipoMembresiaUseCase;
    private final AccessControlService accessControl;

    public TipoMembresiaController(TipoMembresiaUseCase tipoMembresiaUseCase,
                                   AccessControlService accessControl) {
        this.tipoMembresiaUseCase = tipoMembresiaUseCase;
        this.accessControl = accessControl;
    }

    @Operation(summary = "Listar tipos de membresía", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200"),
            @ApiResponse(responseCode = "403")
    })
    @GetMapping
    public Flux<TipoMembresiaResponse> listar() {
        return extractPrincipal()
                .flatMapMany(principal -> tipoMembresiaUseCase.listarActivos(principal.getIdCompania())
                        .map(TipoMembresiaResponse::from));
    }

    @Operation(summary = "Crear tipo de membresía", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201"),
            @ApiResponse(responseCode = "403")
    })
    @PostMapping
    public Mono<ResponseEntity<TipoMembresiaResponse>> crear(@Valid @RequestBody TipoMembresiaRequest request) {
        return extractPrincipal()
                .flatMap(principal -> accessControl.requireAdminOrDueno(principal, principal.getIdCompania())
                        .then(tipoMembresiaUseCase.crear(
                                principal.getIdCompania(),
                                principal.getIdCompania(),
                                new TipoMembresiaUseCase.CrearTipoCommand(
                                        request.nombre(),
                                        TipoMembresia.ModoControl.valueOf(request.modoControl()),
                                        TipoMembresia.DuracionTipo.valueOf(request.duracionTipo()),
                                        request.duracionValor(),
                                        request.diasAcceso(),
                                        request.precio()
                                )
                        ))
                )
                .map(t -> ResponseEntity.status(HttpStatus.CREATED).body(TipoMembresiaResponse.from(t)));
    }

    @Operation(summary = "Actualizar tipo de membresía", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200"),
            @ApiResponse(responseCode = "404"),
            @ApiResponse(responseCode = "403")
    })
    @PutMapping("/{id}")
    public Mono<ResponseEntity<TipoMembresiaResponse>> actualizar(@PathVariable Long id,
                                                                   @RequestBody TipoMembresiaRequest request) {
        return extractPrincipal()
                .flatMap(principal -> accessControl.requireAdminOrDueno(principal, principal.getIdCompania())
                        .then(tipoMembresiaUseCase.actualizar(
                                id, principal.getIdCompania(),
                                new TipoMembresiaUseCase.ActualizarTipoCommand(request.nombre(), request.precio())
                        ))
                )
                .map(t -> ResponseEntity.ok(TipoMembresiaResponse.from(t)));
    }

    @Operation(summary = "Desactivar tipo de membresía", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200"),
            @ApiResponse(responseCode = "404"),
            @ApiResponse(responseCode = "403")
    })
    @PutMapping("/{id}/desactivar")
    public Mono<ResponseEntity<Void>> desactivar(@PathVariable Long id) {
        return extractPrincipal()
                .flatMap(principal -> accessControl.requireAdminOrDueno(principal, principal.getIdCompania())
                        .then(tipoMembresiaUseCase.desactivar(id, principal.getIdCompania()))
                )
                .thenReturn(ResponseEntity.<Void>ok().build());
    }

    private Mono<JwtPrincipal> extractPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication().getPrincipal())
                .cast(JwtPrincipal.class);
    }
}
