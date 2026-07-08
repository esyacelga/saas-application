package com.gymadmin.attendance.infrastructure.adapter.in.web;

import com.gymadmin.attendance.application.service.AccessControlService;
import com.gymadmin.attendance.domain.model.PlantillaMensaje;
import com.gymadmin.attendance.domain.port.in.PlantillaMensajeUseCase;
import com.gymadmin.attendance.infrastructure.adapter.in.web.dto.ActualizarPlantillaRequest;
import com.gymadmin.attendance.infrastructure.adapter.in.web.dto.PlantillaRequest;
import com.gymadmin.attendance.infrastructure.config.JwtPrincipal;
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

@RestController
@RequestMapping("/api/v1/plantillas")
@RequiredArgsConstructor
public class PlantillaController {

    private final PlantillaMensajeUseCase plantillaUseCase;
    private final AccessControlService accessControl;

    @GetMapping
    public Flux<PlantillaMensaje> listar() {
        return getJwtPrincipal()
                .flatMapMany(principal -> accessControl.requireStaffOrPlataforma(principal)
                        .thenMany(plantillaUseCase.listar(principal.getIdCompania().intValue())));
    }

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
