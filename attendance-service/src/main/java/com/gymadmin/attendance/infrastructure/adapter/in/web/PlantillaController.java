package com.gymadmin.attendance.infrastructure.adapter.in.web;

import com.gymadmin.attendance.application.service.AccessControlService;
import com.gymadmin.attendance.domain.model.PlantillaMensaje;
import com.gymadmin.attendance.domain.port.in.PlantillaMensajeUseCase;
import com.gymadmin.attendance.infrastructure.adapter.in.web.dto.ActualizarPlantillaRequest;
import com.gymadmin.attendance.infrastructure.adapter.in.web.dto.PlantillaRequest;
import com.gymadmin.attendance.infrastructure.config.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Tag(name = "Plantillas", description = "Plantillas de mensajes de notificación")
@RestController
@RequestMapping("/api/v1/plantillas")
@RequiredArgsConstructor
public class PlantillaController {

    private final PlantillaMensajeUseCase plantillaUseCase;
    private final AccessControlService accessControl;

    @Operation(summary = "Listar plantillas", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de plantillas de la compañía"),
            @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado — se requiere rol staff o plataforma")
    })
    @GetMapping
    public Flux<PlantillaMensaje> listar() {
        return getJwtPrincipal()
                .flatMapMany(principal -> accessControl.requireStaffOrPlataforma(principal)
                        .thenMany(plantillaUseCase.listar(principal.getIdCompania().intValue())));
    }

    @Operation(summary = "Crear plantilla", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Plantilla creada correctamente"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos"),
            @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado — se requiere rol dueño, admin_compania o plataforma")
    })
    @PostMapping
    public Mono<ResponseEntity<PlantillaMensaje>> crear(@Valid @RequestBody PlantillaRequest request) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireDuenoOrPlataforma(principal)
                        .then(plantillaUseCase.crear(new PlantillaMensajeUseCase.CrearPlantillaCommand(
                                principal.getIdCompania().intValue(),
                                1, // sucursal por defecto; ajustar según header o JWT
                                request.tipo(),
                                request.nombre(),
                                request.contenido()
                        )))
                        .map(p -> ResponseEntity.status(HttpStatus.CREATED).body(p)));
    }

    @Operation(summary = "Actualizar plantilla", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plantilla actualizada correctamente"),
            @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos"),
            @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado — se requiere rol dueño, admin_compania o plataforma"),
            @ApiResponse(responseCode = "404", description = "Plantilla no encontrada")
    })
    @PutMapping("/{id}")
    public Mono<ResponseEntity<PlantillaMensaje>> actualizar(
            @PathVariable Integer id,
            @RequestBody ActualizarPlantillaRequest request) {

        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireDuenoOrPlataforma(principal)
                        .then(plantillaUseCase.actualizar(id,
                                new PlantillaMensajeUseCase.ActualizarPlantillaCommand(
                                        request.contenido(), request.activo(), request.nombre()),
                                principal.getIdCompania().intValue()))
                        .map(ResponseEntity::ok));
    }

    @Operation(summary = "Eliminar plantilla", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Plantilla eliminada correctamente"),
            @ApiResponse(responseCode = "401", description = "Token JWT inválido o ausente"),
            @ApiResponse(responseCode = "403", description = "Acceso denegado — se requiere rol dueño, admin_compania o plataforma"),
            @ApiResponse(responseCode = "404", description = "Plantilla no encontrada")
    })
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> eliminar(@PathVariable Integer id) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireDuenoOrPlataforma(principal)
                        .then(plantillaUseCase.eliminar(id, principal.getIdCompania().intValue()))
                        .thenReturn(ResponseEntity.<Void>noContent().build()));
    }

    private Mono<JwtPrincipal> getJwtPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(JwtPrincipal.class);
    }
}
