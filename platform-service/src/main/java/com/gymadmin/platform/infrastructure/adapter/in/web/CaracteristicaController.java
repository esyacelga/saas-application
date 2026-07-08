package com.gymadmin.platform.infrastructure.adapter.in.web;

import com.gymadmin.platform.application.service.AccessControlService;
import com.gymadmin.platform.domain.model.Caracteristica;
import com.gymadmin.platform.domain.port.in.CaracteristicaUseCase;
import com.gymadmin.platform.infrastructure.adapter.in.web.dto.CaracteristicaDto;
import com.gymadmin.platform.infrastructure.adapter.in.web.dto.CaracteristicaRequest;
import com.gymadmin.platform.infrastructure.config.JwtPrincipal;
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
@RequestMapping("/api/v1/caracteristicas")
public class CaracteristicaController {

    private final CaracteristicaUseCase caracteristicaUseCase;
    private final AccessControlService accessControl;

    public CaracteristicaController(CaracteristicaUseCase caracteristicaUseCase,
                                    AccessControlService accessControl) {
        this.caracteristicaUseCase = caracteristicaUseCase;
        this.accessControl = accessControl;
    }

    @GetMapping
    public Flux<CaracteristicaDto> listarCaracteristicas() {
        return getJwtPrincipal()
                .flatMapMany(principal -> accessControl.requirePlataforma(principal)
                        .thenMany(caracteristicaUseCase.listarCaracteristicas().map(this::toDto)));
    }

    @PostMapping
    public Mono<ResponseEntity<CaracteristicaDto>> crearCaracteristica(
            @Valid @RequestBody CaracteristicaRequest request) {
        return getJwtPrincipal()
                .flatMap(principal -> accessControl.requireSuperAdmin(principal)
                        .then(caracteristicaUseCase.crearCaracteristica(
                                new CaracteristicaUseCase.CrearCaracteristicaCommand(
                                        request.codigo(),
                                        request.nombre(),
                                        request.modulo()
                                )))
                        .map(c -> ResponseEntity.status(HttpStatus.CREATED).body(toDto(c))));
    }

    private Mono<JwtPrincipal> getJwtPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(JwtPrincipal.class);
    }

    private CaracteristicaDto toDto(Caracteristica c) {
        return new CaracteristicaDto(c.getId(), c.getCodigo(), c.getNombre(), c.getModulo(), c.getActivo());
    }
}
