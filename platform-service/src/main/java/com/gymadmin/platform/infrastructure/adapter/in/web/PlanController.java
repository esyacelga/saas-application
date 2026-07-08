package com.gymadmin.platform.infrastructure.adapter.in.web;

import com.gymadmin.platform.application.service.AccessControlService;
import com.gymadmin.platform.domain.model.Caracteristica;
import com.gymadmin.platform.domain.model.Plan;
import com.gymadmin.platform.domain.port.in.ActividadPlataformaUseCase;
import com.gymadmin.platform.domain.port.in.PlanUseCase;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/planes")
@Tag(name = "Planes", description = "Planes de suscripción SaaS")
public class PlanController {

    private final PlanUseCase planUseCase;
    private final AccessControlService accessControl;
    private final ActividadPlataformaUseCase actividadUseCase;

    public PlanController(PlanUseCase planUseCase,
                           AccessControlService accessControl,
                           ActividadPlataformaUseCase actividadUseCase) {
        this.planUseCase = planUseCase;
        this.accessControl = accessControl;
        this.actividadUseCase = actividadUseCase;
    }

    @Operation(summary = "Listar planes activos (público, sin auth)")
    @ApiResponse(responseCode = "200", description = "Lista de planes activos")
    @GetMapping("/publicos")
    public Flux<PlanResponse> listarPlanesPublicos() {
        return planUseCase.listarPlanesPublicos().map(this::toResponse);
    }

    @Operation(summary = "Listar todos los planes (plataforma)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de planes"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @GetMapping
    public Flux<PlanResponse> listarPlanes() {
        return getJwtPrincipal()
                .flatMapMany(principal -> accessControl.requirePlataforma(principal)
                        .thenMany(planUseCase.listarPlanes().map(this::toResponse)));
    }

    @Operation(summary = "Crear un nuevo plan (super_admin)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Plan creado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    @PostMapping
    public Mono<ResponseEntity<PlanResponse>> crearPlan(@Valid @RequestBody PlanRequest request) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireSuperAdmin(principal)
                        .then(planUseCase.crearPlan(new PlanUseCase.CrearPlanCommand(
                                request.nombre(),
                                request.descripcion(),
                                request.precioMensual())))
                        .flatMap(plan -> actividadUseCase.registrar(new ActividadPlataformaUseCase.RegistrarCommand(
                                "PLAN_CREADO", "planes", plan.getId(), plan.getNombre(), null, principal.getName()
                        )).onErrorResume(e -> Mono.empty()).thenReturn(plan))
                        .map(plan -> ResponseEntity.status(HttpStatus.CREATED).body(toResponse(plan))));
    }

    @Operation(summary = "Actualizar un plan existente (super_admin)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Plan actualizado"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "Plan no encontrado")
    })
    @PutMapping("/{id}")
    public Mono<ResponseEntity<PlanResponse>> actualizarPlan(@PathVariable Long id,
                                                              @Valid @RequestBody PlanRequest request) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireSuperAdmin(principal)
                        .then(planUseCase.actualizarPlan(id, new PlanUseCase.ActualizarPlanCommand(
                                request.nombre(),
                                request.descripcion(),
                                request.precioMensual())))
                        .flatMap(plan -> actividadUseCase.registrar(new ActividadPlataformaUseCase.RegistrarCommand(
                                "PLAN_ACTUALIZADO", "planes", plan.getId(), plan.getNombre(), null, principal.getName()
                        )).onErrorResume(e -> Mono.empty()).thenReturn(plan))
                        .map(plan -> ResponseEntity.ok(toResponse(plan))));
    }

    @Operation(summary = "Asignar características a un plan (super_admin)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Características asignadas"),
        @ApiResponse(responseCode = "400", description = "Datos inválidos"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "Plan no encontrado")
    })
    @PutMapping("/{id}/caracteristicas")
    public Mono<ResponseEntity<PlanResponse>> asignarCaracteristicas(@PathVariable Long id,
                                                                       @Valid @RequestBody AsignarCaracteristicasRequest request) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireSuperAdmin(principal)
                        .then(planUseCase.asignarCaracteristicas(id, request.caracteristicaIds()))
                        .map(plan -> ResponseEntity.ok(toResponse(plan))));
    }

    @Operation(summary = "Desactivar un plan (super_admin)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Plan desactivado"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "Plan no encontrado")
    })
    @PutMapping("/{id}/desactivar")
    public Mono<ResponseEntity<Void>> desactivarPlan(@PathVariable Long id) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireSuperAdmin(principal)
                        .then(planUseCase.desactivarPlan(id))
                        .thenReturn(ResponseEntity.<Void>noContent().build()));
    }

    private Mono<JwtPrincipal> getJwtPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(JwtPrincipal.class);
    }

    private PlanResponse toResponse(Plan plan) {
        List<CaracteristicaDto> caracs = plan.getCaracteristicas() != null
                ? plan.getCaracteristicas().stream().map(this::toCaracteristicaDto).toList()
                : List.of();
        return new PlanResponse(
                plan.getId(),
                plan.getNombre(),
                plan.getDescripcion(),
                plan.getPrecioMensual(),
                plan.getActivo(),
                caracs
        );
    }

    private CaracteristicaDto toCaracteristicaDto(Caracteristica c) {
        return new CaracteristicaDto(c.getId(), c.getCodigo(), c.getNombre(), c.getModulo(), c.getActivo());
    }
}
