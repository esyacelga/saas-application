package com.gymadmin.finance.infrastructure.adapter.in.web;

import com.gymadmin.finance.application.service.AccessControlService;
import com.gymadmin.finance.domain.model.CategoriaIngreso;
import com.gymadmin.finance.domain.port.in.CategoriaIngresoUseCase;
import com.gymadmin.finance.infrastructure.adapter.in.web.dto.CrearCategoriaRequest;
import com.gymadmin.finance.infrastructure.config.JwtPrincipal;
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
@RequestMapping("/api/v1/finanzas/categorias-ingreso")
@RequiredArgsConstructor
public class CategoriaIngresoController {

    private final CategoriaIngresoUseCase categoriaUseCase;
    private final AccessControlService accessControl;

    @GetMapping
    public Flux<CategoriaIngreso> listar(@RequestParam(required = false) Integer idSucursal) {
        return getJwtPrincipal()
                .flatMapMany(p -> accessControl.requireFinanzasLeer(p)
                        .thenMany(Flux.defer(() -> categoriaUseCase.listar(
                                p.getIdCompania().intValue(), idSucursal))));
    }

    @PostMapping
    public Mono<ResponseEntity<CategoriaIngreso>> crear(@Valid @RequestBody CrearCategoriaRequest request) {
        return getJwtPrincipal()
                .flatMap(p -> accessControl.requireFinanzasCrear(p)
                        .then(Mono.defer(() -> categoriaUseCase.crear(new CategoriaIngresoUseCase.CrearCommand(
                                p.getIdCompania().intValue(),
                                p.getIdSucursal() != null ? p.getIdSucursal().intValue() : 1,
                                request.nombre()
                        ))))
                        .map(cat -> ResponseEntity.status(HttpStatus.CREATED).body(cat)));
    }

    @PutMapping("/{id}/desactivar")
    public Mono<ResponseEntity<CategoriaIngreso>> desactivar(@PathVariable Integer id) {
        return getJwtPrincipal()
                .flatMap(p -> accessControl.requireFinanzasCrear(p)
                        .then(Mono.defer(() -> categoriaUseCase.desactivar(id, p.getIdCompania().intValue())))
                        .map(cat -> ResponseEntity.ok(cat)));
    }

    private Mono<JwtPrincipal> getJwtPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(JwtPrincipal.class);
    }
}
