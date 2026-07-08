package com.gymadmin.platform.infrastructure.adapter.in.web;

import com.gymadmin.platform.application.service.AccessControlService;
import com.gymadmin.platform.application.service.CloudinaryService;
import com.gymadmin.platform.domain.model.Compania;
import com.gymadmin.platform.domain.port.in.ActividadPlataformaUseCase;
import com.gymadmin.platform.domain.port.in.CompaniaUseCase;
import com.gymadmin.platform.infrastructure.adapter.in.web.dto.*;
import com.gymadmin.platform.infrastructure.config.JwtPrincipal;
import jakarta.validation.Valid;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/companias")
public class CompaniaController {

    private final CompaniaUseCase companiaUseCase;
    private final AccessControlService accessControl;
    private final ActividadPlataformaUseCase actividadUseCase;
    private final CloudinaryService cloudinaryService;

    public CompaniaController(CompaniaUseCase companiaUseCase,
                               AccessControlService accessControl,
                               ActividadPlataformaUseCase actividadUseCase,
                               CloudinaryService cloudinaryService) {
        this.companiaUseCase = companiaUseCase;
        this.accessControl = accessControl;
        this.actividadUseCase = actividadUseCase;
        this.cloudinaryService = cloudinaryService;
    }

    @GetMapping
    public Flux<CompaniaResponse> listarCompanias() {
        return getJwtPrincipal()
                .flatMapMany(principal -> accessControl.requirePlataforma(principal)
                        .thenMany(companiaUseCase.listarCompanias(principal)
                                .map(this::toResponse)));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<CompaniaResponse>> getCompania(@PathVariable Long id) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requirePlataforma(principal)
                        .then(companiaUseCase.getCompania(id))
                        .map(c -> ResponseEntity.ok(toResponse(c))));
    }

    @PostMapping
    public Mono<ResponseEntity<RegistrarGymResponse>> registrarGym(
            @Valid @RequestBody RegistrarGymRequest request) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireSuperAdmin(principal)
                        .then(companiaUseCase.registrarGym(new CompaniaUseCase.RegistrarGymCommand(
                                request.nombre(),
                                request.ruc(),
                                request.logoUrl(),
                                request.telefono(),
                                request.whatsapp(),
                                request.correo(),
                                request.idPlan(),
                                request.nombreSucursal(),
                                request.direccionSucursal()
                        )))
                        .flatMap(result -> actividadUseCase.registrar(new ActividadPlataformaUseCase.RegistrarCommand(
                                "COMPANIA_CREADA", "companias", result.idCompania(), request.nombre(), null, principal.getName()
                        )).onErrorResume(e -> Mono.empty()).thenReturn(result))
                        .map(result -> ResponseEntity.status(HttpStatus.CREATED).body(
                                new RegistrarGymResponse(
                                        result.idCompania(),
                                        result.idCompaniaPlan(),
                                        result.idSucursal(),
                                        result.qrToken()
                                )
                        )));
    }

    @PostMapping("/auto-registro")
    public Mono<ResponseEntity<RegistrarGymWizardResponse>> autoRegistro(
            @Valid @RequestBody RegistrarGymWizardRequest request) {
        return companiaUseCase.registrarGymWizard(new CompaniaUseCase.RegistrarGymWizardCommand(
                        request.nombre(),
                        request.ruc(),
                        null,
                        request.telefono(),
                        request.whatsapp(),
                        request.correo(),
                        request.idPlan(),
                        request.nombreSucursal(),
                        request.direccionSucursal(),
                        new CompaniaUseCase.UsuarioWizardCommand(
                                null,
                                request.usuarioPrincipal().ci(),
                                request.usuarioPrincipal().nombre(),
                                request.usuarioPrincipal().telefono(),
                                request.usuarioPrincipal().correo(),
                                request.usuarioPrincipal().password()
                        ),
                        List.of()
                ))
                .map(result -> ResponseEntity.status(HttpStatus.CREATED).body(
                        new RegistrarGymWizardResponse(
                                result.idCompania(),
                                result.idCompaniaPlan(),
                                result.idSucursal(),
                                result.qrToken(),
                                new UsuarioCreadoDto(
                                        result.usuarioPrincipal().id(),
                                        result.usuarioPrincipal().idPersona(),
                                        result.usuarioPrincipal().correo()
                                ),
                                result.usuariosCreados()
                        )
                ));
    }

    @PostMapping("/wizard")
    public Mono<ResponseEntity<RegistrarGymWizardResponse>> registrarGymWizard(
            @Valid @RequestBody RegistrarGymWizardRequest request) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireSuperAdmin(principal)
                        .then(companiaUseCase.registrarGymWizard(new CompaniaUseCase.RegistrarGymWizardCommand(
                                request.nombre(),
                                request.ruc(),
                                request.logoUrl(),
                                request.telefono(),
                                request.whatsapp(),
                                request.correo(),
                                request.idPlan(),
                                request.nombreSucursal(),
                                request.direccionSucursal(),
                                new CompaniaUseCase.UsuarioWizardCommand(
                                        request.usuarioPrincipal().idPersona(),
                                        request.usuarioPrincipal().ci(),
                                        request.usuarioPrincipal().nombre(),
                                        request.usuarioPrincipal().telefono(),
                                        request.usuarioPrincipal().correo(),
                                        request.usuarioPrincipal().password()
                                ),
                                request.usuariosAdicionales() == null ? List.of() :
                                        request.usuariosAdicionales().stream()
                                                .map(u -> new CompaniaUseCase.UsuarioWizardCommand(
                                                        u.idPersona(), u.ci(), u.nombre(), u.telefono(), u.correo(), u.password()))
                                                .toList()
                        )))
                        .flatMap(result -> actividadUseCase.registrar(new ActividadPlataformaUseCase.RegistrarCommand(
                                "COMPANIA_CREADA", "companias", result.idCompania(), request.nombre(), null, principal.getName()
                        )).onErrorResume(e -> Mono.empty()).thenReturn(result))
                        .map(result -> ResponseEntity.status(HttpStatus.CREATED).body(
                                new RegistrarGymWizardResponse(
                                        result.idCompania(),
                                        result.idCompaniaPlan(),
                                        result.idSucursal(),
                                        result.qrToken(),
                                        new UsuarioCreadoDto(
                                                result.usuarioPrincipal().id(),
                                                result.usuarioPrincipal().idPersona(),
                                                result.usuarioPrincipal().correo()
                                        ),
                                        result.usuariosCreados()
                                )
                        )));
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<CompaniaResponse>> actualizarCompania(@PathVariable Long id,
                                                                      @RequestBody ActualizarCompaniaRequest request) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requirePlataforma(principal)
                        .then(companiaUseCase.actualizarCompania(id,
                                new CompaniaUseCase.ActualizarCompaniaCommand(
                                        request.nombre(),
                                        request.logoUrl(),
                                        request.telefono(),
                                        request.whatsapp(),
                                        request.correo()
                                ), principal))
                        .flatMap(compania -> actividadUseCase.registrar(new ActividadPlataformaUseCase.RegistrarCommand(
                                "COMPANIA_ACTUALIZADA", "companias", compania.getId(), compania.getNombre(), null, principal.getName()
                        )).onErrorResume(e -> Mono.empty()).thenReturn(compania))
                        .map(compania -> ResponseEntity.ok(toResponse(compania))));
    }

    @PutMapping("/{id}/suspender")
    public Mono<ResponseEntity<Void>> suspenderCompania(@PathVariable Long id,
                                                         @Valid @RequestBody SuspenderRequest request) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireSuperAdmin(principal)
                        .then(companiaUseCase.suspenderCompania(id, request.motivo()))
                        .then(actividadUseCase.registrar(new ActividadPlataformaUseCase.RegistrarCommand(
                                "COMPANIA_SUSPENDIDA", "companias", id, null, request.motivo(), principal.getName()
                        )).onErrorResume(e -> Mono.empty()))
                        .thenReturn(ResponseEntity.<Void>noContent().build()));
    }

    @PostMapping(value = "/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<ResponseEntity<CompaniaResponse>> subirLogo(@PathVariable Long id,
                                                             @RequestPart("file") FilePart filePart) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requirePlataforma(principal)
                        .then(DataBufferUtils.join(filePart.content())
                                .map(buf -> {
                                    byte[] bytes = new byte[buf.readableByteCount()];
                                    buf.read(bytes);
                                    DataBufferUtils.release(buf);
                                    return bytes;
                                }))
                        .flatMap(bytes -> cloudinaryService.subirLogo(bytes, id))
                        .flatMap(logoUrl -> companiaUseCase.actualizarCompania(id,
                                new CompaniaUseCase.ActualizarCompaniaCommand(null, logoUrl, null, null, null),
                                principal))
                        .map(c -> ResponseEntity.ok(toResponse(c))));
    }

    private Mono<JwtPrincipal> getJwtPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(JwtPrincipal.class);
    }

    private CompaniaResponse toResponse(Compania c) {
        return new CompaniaResponse(
                c.getId(),
                c.getNombre(),
                c.getRuc(),
                c.getTelefono(),
                c.getWhatsapp(),
                c.getCorreo(),
                c.getLogoUrl(),
                c.getActivo(),
                null
        );
    }
}
