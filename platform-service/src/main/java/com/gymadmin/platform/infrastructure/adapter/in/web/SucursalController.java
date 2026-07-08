package com.gymadmin.platform.infrastructure.adapter.in.web;

import com.gymadmin.platform.application.service.AccessControlService;
import com.gymadmin.platform.domain.model.Sucursal;
import com.gymadmin.platform.domain.port.in.SucursalUseCase;
import com.gymadmin.platform.infrastructure.adapter.in.web.dto.*;
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
@Tag(name = "Sucursales", description = "Sucursales de una compañía")
public class SucursalController {

    private final SucursalUseCase sucursalUseCase;
    private final AccessControlService accessControl;

    public SucursalController(SucursalUseCase sucursalUseCase, AccessControlService accessControl) {
        this.sucursalUseCase = sucursalUseCase;
        this.accessControl = accessControl;
    }

    @Operation(summary = "Listar sucursales de una compañía", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de sucursales"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @GetMapping("/api/v1/companias/{idCompania}/sucursales")
    public Flux<SucursalResponse> listarSucursales(@PathVariable Long idCompania) {
        return getJwtPrincipal()
                .flatMapMany(principal -> accessControl.requirePlataforma(principal)
                        .thenMany(sucursalUseCase.listarSucursales(idCompania).map(this::toResponse)));
    }

    @Operation(summary = "Crear una nueva sucursal para una compañía", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Sucursal creada"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @PostMapping("/api/v1/companias/{idCompania}/sucursales")
    public Mono<ResponseEntity<SucursalResponse>> crearSucursal(@PathVariable Long idCompania,
                                                                  @Valid @RequestBody CrearSucursalRequest request) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requirePlataforma(principal)
                        .then(sucursalUseCase.crearSucursal(idCompania,
                                new SucursalUseCase.CrearSucursalCommand(
                                        request.nombre(),
                                        request.direccion(),
                                        request.esPrincipal()
                                ), principal))
                        .map(sucursal -> ResponseEntity.status(HttpStatus.CREATED).body(toResponse(sucursal))));
    }

    @Operation(summary = "Actualizar datos de una sucursal", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sucursal actualizada"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "Sucursal no encontrada")
    })
    @PutMapping("/api/v1/sucursales/{id}")
    public Mono<ResponseEntity<SucursalResponse>> actualizarSucursal(@PathVariable Long id,
                                                                       @RequestBody ActualizarSucursalRequest request) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requirePlataforma(principal)
                        .then(sucursalUseCase.actualizarSucursal(id,
                                new SucursalUseCase.ActualizarSucursalCommand(
                                        request.nombre(),
                                        request.direccion()
                                )))
                        .map(sucursal -> ResponseEntity.ok(toResponse(sucursal))));
    }

    @Operation(summary = "Renovar token QR de una sucursal (super_admin)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "QR renovado"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "Sucursal no encontrada")
    })
    @PostMapping("/api/v1/sucursales/{id}/qr/renovar")
    public Mono<ResponseEntity<QrRenovarResponse>> renovarQrToken(@PathVariable Long id,
                                                                    @RequestBody(required = false) QrRenovarRequest request) {
        Integer expiresInHours = request != null ? request.expiresInHours() : null;
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireSuperAdmin(principal)
                        .then(sucursalUseCase.renovarQrToken(id, expiresInHours))
                        .map(result -> ResponseEntity.ok(
                                new QrRenovarResponse(result.qrToken(), result.qrTokenExpira()))));
    }

    private Mono<JwtPrincipal> getJwtPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(JwtPrincipal.class);
    }

    private SucursalResponse toResponse(Sucursal s) {
        return new SucursalResponse(
                s.getId(),
                s.getIdCompania(),
                s.getNombre(),
                s.getDireccion(),
                s.getEsPrincipal(),
                s.getActivo(),
                s.getQrToken(),
                s.getQrTokenExpira()
        );
    }
}
