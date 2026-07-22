package com.gymadmin.platform.infrastructure.adapter.in.web;

import com.gymadmin.platform.application.service.AccessControlService;
import com.gymadmin.platform.application.service.CloudinaryService;
import com.gymadmin.platform.domain.model.Compania;
import com.gymadmin.platform.domain.model.CompaniaConPlan;
import com.gymadmin.platform.domain.port.in.ActividadPlataformaUseCase;
import com.gymadmin.platform.domain.port.in.CompaniaUseCase;
import com.gymadmin.platform.domain.port.in.EnviarRecordatorioVencimientoUseCase;
import com.gymadmin.platform.infrastructure.adapter.in.web.dto.*;
import com.gymadmin.platform.infrastructure.config.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/v1/companias")
@Tag(name = "Compañías", description = "Gestión de compañías/gimnasios")
public class CompaniaController {

    private final CompaniaUseCase companiaUseCase;
    private final AccessControlService accessControl;
    private final ActividadPlataformaUseCase actividadUseCase;
    private final CloudinaryService cloudinaryService;
    private final EnviarRecordatorioVencimientoUseCase enviarRecordatorioUseCase;

    public CompaniaController(CompaniaUseCase companiaUseCase,
                               AccessControlService accessControl,
                               ActividadPlataformaUseCase actividadUseCase,
                               CloudinaryService cloudinaryService,
                               EnviarRecordatorioVencimientoUseCase enviarRecordatorioUseCase) {
        this.companiaUseCase = companiaUseCase;
        this.accessControl = accessControl;
        this.actividadUseCase = actividadUseCase;
        this.cloudinaryService = cloudinaryService;
        this.enviarRecordatorioUseCase = enviarRecordatorioUseCase;
    }

    @Operation(summary = "Listar todas las compañías", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de compañías"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @GetMapping
    public Flux<CompaniaResponse> listarCompanias() {
        return getJwtPrincipal()
                .flatMapMany(principal -> accessControl.requirePlataforma(principal)
                        .thenMany(companiaUseCase.listarCompanias(principal)
                                .map(this::toResponse)));
    }

    @Operation(summary = "Obtener compañía por ID", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Compañía encontrada"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "Compañía no encontrada")
    })
    @GetMapping("/{id}")
    public Mono<ResponseEntity<CompaniaResponse>> getCompania(@PathVariable Long id) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requirePlataforma(principal)
                        .then(companiaUseCase.getCompania(id))
                        .map(c -> ResponseEntity.ok(toResponse(c))));
    }

    @Operation(summary = "Registrar nuevo gimnasio (super_admin)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Gimnasio registrado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
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

    @Operation(summary = "Auto-registro público de un gimnasio (sin auth)")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Gimnasio y usuario principal creados"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos")
    })
    @PostMapping("/auto-registro")
    public Mono<ResponseEntity<RegistrarGymWizardResponse>> autoRegistro(
            @Valid @RequestBody AutoRegistroRequest request) {
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
                        List.of(),
                        // El auto-registro público no pide teléfono ni opt-in (disclosure progresivo):
                        // el dueño lo activa después desde Configuración, donde ya tiene el campo WhatsApp.
                        false
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

    @Operation(summary = "Verificar disponibilidad de correo (público, para el registro)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Devuelve si el correo ya está en uso")
    })
    @GetMapping("/correo-disponible")
    public Mono<CorreoDisponibleResponse> correoDisponible(@RequestParam("correo") String correo) {
        return companiaUseCase.correoEnUso(correo)
                .map(enUso -> new CorreoDisponibleResponse(!enUso, enUso));
    }

    @Operation(summary = "Registro wizard de gimnasio con usuarios (super_admin)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Gimnasio y usuarios creados"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
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
                                                .toList(),
                                Boolean.TRUE.equals(request.aceptaWhatsapp())
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

    @Operation(summary = "Actualizar datos de una compañía", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Compañía actualizada"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "Compañía no encontrada")
    })
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

    @Operation(summary = "Suspender una compañía (super_admin)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Compañía suspendida"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "Compañía no encontrada")
    })
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

    @Operation(summary = "Subir logo de la compañía", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Logo subido y URL actualizada"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "Compañía no encontrada")
    })
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

    @Operation(summary = "Enviar recordatorio de vencimiento por WhatsApp (envío directo)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Recordatorio enviado"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "Compañía no encontrada"),
        @ApiResponse(responseCode = "422", description = "No enviable (no_consentimiento / telefono_invalido / sin_suscripcion)")
    })
    @PostMapping("/{id}/recordatorio-vencimiento")
    public Mono<ResponseEntity<RecordatorioVencimientoResponse>> enviarRecordatorioVencimiento(@PathVariable Long id) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requirePlataforma(principal)
                        .then(enviarRecordatorioUseCase.enviar(id))
                        .flatMap(resultado -> actividadUseCase.registrar(new ActividadPlataformaUseCase.RegistrarCommand(
                                "RECORDATORIO_VENCIMIENTO_ENVIADO", "companias", id, null, resultado.template(), principal.getName()
                        )).onErrorResume(e -> Mono.empty()).thenReturn(resultado))
                        .map(resultado -> ResponseEntity.ok(new RecordatorioVencimientoResponse(
                                resultado.enviado(), resultado.telefono(), resultado.template()))));
    }

    private Mono<JwtPrincipal> getJwtPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(JwtPrincipal.class);
    }

    private CompaniaResponse toResponse(Compania c) {
        return toResponse(c, null);
    }

    private CompaniaResponse toResponse(CompaniaConPlan cp) {
        CompaniaConPlan.PlanActivo plan = cp.planActivo();
        CompaniaResponse.PlanActivoDto planDto = plan == null ? null
                : new CompaniaResponse.PlanActivoDto(
                        plan.nombre(),
                        plan.estado() != null ? plan.estado().name() : null,
                        plan.fechaFin(),
                        plan.fechaFin() != null ? ChronoUnit.DAYS.between(LocalDate.now(), plan.fechaFin()) : null
                );
        return toResponse(cp.compania(), planDto);
    }

    private CompaniaResponse toResponse(Compania c, CompaniaResponse.PlanActivoDto planActivo) {
        return new CompaniaResponse(
                c.getId(),
                c.getNombre(),
                c.getRuc(),
                c.getTelefono(),
                c.getWhatsapp(),
                c.getCorreo(),
                c.getLogoUrl(),
                c.getActivo(),
                planActivo,
                c.isAceptaWhatsapp(),
                c.getFechaConsentimientoWa()
        );
    }
}
