package com.gymadmin.platform.infrastructure.adapter.in.web;

import com.gymadmin.platform.application.service.AccessControlService;
import com.gymadmin.platform.application.service.CloudinaryService;
import com.gymadmin.platform.domain.model.Compania;
import com.gymadmin.platform.domain.model.Sucursal;
import com.gymadmin.platform.domain.port.in.CompaniaUseCase;
import com.gymadmin.platform.domain.port.in.SucursalUseCase;
import com.gymadmin.platform.infrastructure.adapter.in.web.dto.ActualizarMiEmpresaRequest;
import com.gymadmin.platform.infrastructure.adapter.in.web.dto.ActualizarMiSucursalRequest;
import com.gymadmin.platform.infrastructure.adapter.in.web.dto.CompaniaResponse;
import com.gymadmin.platform.infrastructure.adapter.in.web.dto.MiSucursalResponse;
import com.gymadmin.platform.infrastructure.config.JwtPrincipal;
import com.gymadmin.platform.infrastructure.exception.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/mi-empresa")
@Tag(name = "Mi Empresa", description = "Datos de la propia compañía del usuario autenticado")
public class MiEmpresaController {

    private final CompaniaUseCase companiaUseCase;
    private final SucursalUseCase sucursalUseCase;
    private final CloudinaryService cloudinaryService;
    private final AccessControlService accessControl;

    public MiEmpresaController(CompaniaUseCase companiaUseCase,
                                SucursalUseCase sucursalUseCase,
                                CloudinaryService cloudinaryService,
                                AccessControlService accessControl) {
        this.companiaUseCase = companiaUseCase;
        this.sucursalUseCase = sucursalUseCase;
        this.cloudinaryService = cloudinaryService;
        this.accessControl = accessControl;
    }

    @Operation(summary = "Obtener datos de mi empresa", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Datos de la empresa"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @GetMapping
    public Mono<ResponseEntity<CompaniaResponse>> getMiEmpresa() {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireStaff(principal)
                        .then(companiaUseCase.getCompania(principal.getIdCompania()))
                        .map(c -> ResponseEntity.ok(toResponse(c))));
    }

    @Operation(summary = "Actualizar datos de mi empresa", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Empresa actualizada"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @PatchMapping
    public Mono<ResponseEntity<CompaniaResponse>> actualizarMiEmpresa(
            @RequestBody ActualizarMiEmpresaRequest request) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireStaff(principal)
                        .then(companiaUseCase.actualizarCompania(
                                principal.getIdCompania(),
                                new CompaniaUseCase.ActualizarCompaniaCommand(
                                        request.nombre(),
                                        null,
                                        request.telefono(),
                                        request.whatsapp(),
                                        request.correo()
                                ),
                                principal
                        ))
                        .map(c -> ResponseEntity.ok(toResponse(c))));
    }

    @Operation(summary = "Subir logo de mi empresa", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Logo subido y URL actualizada"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<CompaniaResponse>> subirLogo(
            @RequestPart("file") FilePart filePart) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireStaff(principal)
                        .then(DataBufferUtils.join(filePart.content())
                                .map(buf -> {
                                    byte[] bytes = new byte[buf.readableByteCount()];
                                    buf.read(bytes);
                                    DataBufferUtils.release(buf);
                                    return bytes;
                                }))
                        .flatMap(bytes -> cloudinaryService.subirLogo(bytes, principal.getIdCompania()))
                        .flatMap(logoUrl -> companiaUseCase.actualizarCompania(
                                principal.getIdCompania(),
                                new CompaniaUseCase.ActualizarCompaniaCommand(null, logoUrl, null, null, null),
                                principal
                        ))
                        .map(c -> ResponseEntity.ok(toResponse(c))));
    }

    @Operation(summary = "Obtener datos de mi sucursal", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Datos de la sucursal"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "Sucursal no encontrada")
    })
    @GetMapping("/sucursal")
    public Mono<ResponseEntity<MiSucursalResponse>> getMiSucursal() {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireStaff(principal)
                        .then(getSucursalByPrincipal(principal))
                        .map(s -> ResponseEntity.ok(toSucursalResponse(s))));
    }

    @Operation(summary = "Actualizar datos de mi sucursal", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sucursal actualizada"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @PatchMapping("/sucursal")
    public Mono<ResponseEntity<MiSucursalResponse>> actualizarMiSucursal(
            @RequestBody ActualizarMiSucursalRequest request) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireStaff(principal)
                        .then(sucursalUseCase.actualizarSucursal(
                                principal.getIdSucursal(),
                                new SucursalUseCase.ActualizarSucursalCommand(
                                        request.nombre(),
                                        request.direccion()
                                )
                        ))
                        .map(s -> ResponseEntity.ok(toSucursalResponse(s))));
    }

    @Operation(summary = "Renovar token QR de mi sucursal", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "QR renovado"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "Sucursal no encontrada")
    })
    @PostMapping("/sucursal/qr/renovar")
    public Mono<ResponseEntity<MiSucursalResponse>> renovarMiQr() {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireStaff(principal)
                        .then(sucursalUseCase.renovarQrToken(principal.getIdSucursal(), null))
                        .then(getSucursalByPrincipal(principal))
                        .map(s -> ResponseEntity.ok(toSucursalResponse(s))));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Mono<Sucursal> getSucursalByPrincipal(JwtPrincipal principal) {
        return sucursalUseCase.listarSucursales(principal.getIdCompania())
                .filter(s -> s.getId().equals(principal.getIdSucursal()))
                .next()
                .switchIfEmpty(Mono.error(new NotFoundException("Sucursal", principal.getIdSucursal())));
    }

    private Mono<JwtPrincipal> getJwtPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(JwtPrincipal.class);
    }

    private CompaniaResponse toResponse(Compania c) {
        return new CompaniaResponse(
                c.getId(), c.getNombre(), c.getRuc(),
                c.getTelefono(), c.getWhatsapp(), c.getCorreo(),
                c.getLogoUrl(), c.getActivo(), null,
                c.isAceptaWhatsapp(), c.getFechaConsentimientoWa()
        );
    }

    private MiSucursalResponse toSucursalResponse(Sucursal s) {
        return new MiSucursalResponse(
                s.getId(), s.getNombre(), s.getDireccion(),
                s.getEsPrincipal(), s.getQrToken(), s.getQrTokenExpira()
        );
    }
}
