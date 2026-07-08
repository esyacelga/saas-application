package com.gymadmin.finance.infrastructure.adapter.in.web;

import com.gymadmin.finance.application.service.AccessControlService;
import com.gymadmin.finance.domain.model.CategoriaEgreso;
import com.gymadmin.finance.domain.port.in.CategoriaEgresoUseCase;
import com.gymadmin.finance.infrastructure.adapter.in.web.dto.CrearCategoriaRequest;
import com.gymadmin.finance.infrastructure.config.JwtPrincipal;
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

@Tag(name = "Categorías de Egreso", description = "Catálogo de categorías para clasificar egresos")
@RestController
@RequestMapping("/api/v1/finanzas/categorias-egreso")
@RequiredArgsConstructor
public class CategoriaEgresoController {

    private final CategoriaEgresoUseCase categoriaUseCase;
    private final AccessControlService accessControl;

    @Operation(summary = "Listar categorías de egreso", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Listado de categorías de egreso"),
        @ApiResponse(responseCode = "400", description = "Petición inválida"),
        @ApiResponse(responseCode = "403", description = "Sin permiso")
    })
    @GetMapping
    public Flux<CategoriaEgreso> listar(@RequestParam(required = false) Integer idSucursal) {
        return getJwtPrincipal()
                .flatMapMany(p -> accessControl.requireFinanzasLeer(p)
                        .thenMany(Flux.defer(() -> categoriaUseCase.listar(
                                p.getIdCompania().intValue(), idSucursal))));
    }

    @Operation(summary = "Crear categoría de egreso", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Categoría creada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos"),
        @ApiResponse(responseCode = "403", description = "Sin permiso")
    })
    @PostMapping
    public Mono<ResponseEntity<CategoriaEgreso>> crear(@Valid @RequestBody CrearCategoriaRequest request) {
        return getJwtPrincipal()
                .flatMap(p -> accessControl.requireFinanzasCrear(p)
                        .then(Mono.defer(() -> categoriaUseCase.crear(new CategoriaEgresoUseCase.CrearCommand(
                                p.getIdCompania().intValue(),
                                p.getIdSucursal() != null ? p.getIdSucursal().intValue() : 1,
                                request.nombre()
                        ))))
                        .map(cat -> ResponseEntity.status(HttpStatus.CREATED).body(cat)));
    }

    @Operation(summary = "Desactivar categoría de egreso", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Categoría desactivada exitosamente"),
        @ApiResponse(responseCode = "400", description = "Petición inválida"),
        @ApiResponse(responseCode = "403", description = "Sin permiso"),
        @ApiResponse(responseCode = "404", description = "Categoría no encontrada")
    })
    @PutMapping("/{id}/desactivar")
    public Mono<ResponseEntity<CategoriaEgreso>> desactivar(@PathVariable Integer id) {
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
