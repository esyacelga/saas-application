package com.gymadmin.finance.infrastructure.adapter.in.web;

import com.gymadmin.finance.application.service.AccessControlService;
import com.gymadmin.finance.domain.model.Ingreso;
import com.gymadmin.finance.domain.port.in.IngresoUseCase;
import com.gymadmin.finance.infrastructure.adapter.in.web.dto.CrearIngresoRequest;
import com.gymadmin.finance.infrastructure.config.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Tag(name = "Ingresos", description = "Registro y consulta de ingresos financieros")
@RestController
@RequestMapping("/api/v1/finanzas/ingresos")
@RequiredArgsConstructor
public class IngresoController {

    private final IngresoUseCase ingresoUseCase;
    private final AccessControlService accessControl;

    @Operation(summary = "Listar ingresos con filtros y paginación", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Listado de ingresos"),
        @ApiResponse(responseCode = "400", description = "Parámetros inválidos"),
        @ApiResponse(responseCode = "403", description = "Sin permiso")
    })
    @GetMapping
    public Mono<ResponseEntity<IngresoUseCase.IngresoListResult>> listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer idCategoria,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int limit) {

        return getJwtPrincipal()
                .flatMap(p -> accessControl.requireFinanzasLeer(p)
                        .then(Mono.defer(() -> ingresoUseCase.listar(new IngresoUseCase.ListarCommand(
                                p.getIdCompania().intValue(),
                                desde,
                                hasta,
                                idCategoria,
                                page,
                                limit
                        ))))
                        .map(result -> ResponseEntity.ok(result)));
    }

    @Operation(summary = "Registrar ingreso", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Ingreso registrado exitosamente"),
        @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos"),
        @ApiResponse(responseCode = "403", description = "Sin permiso")
    })
    @PostMapping
    public Mono<ResponseEntity<Ingreso>> registrar(@Valid @RequestBody CrearIngresoRequest request) {
        return getJwtPrincipal()
                .flatMap(p -> accessControl.requireFinanzasCrearORecepcion(p)
                        .then(Mono.defer(() -> {
                            Integer idSucursal = request.idSucursal() != null
                                    ? request.idSucursal()
                                    : (p.getIdSucursal() != null ? p.getIdSucursal().intValue() : 1);
                            Integer idUsuarioRegistro = null;
                            try {
                                idUsuarioRegistro = Integer.parseInt(p.getUserId());
                            } catch (NumberFormatException ignored) {}

                            return ingresoUseCase.registrar(new IngresoUseCase.RegistrarCommand(
                                    p.getIdCompania().intValue(),
                                    idSucursal,
                                    request.idCategoria(),
                                    request.monto(),
                                    request.descripcion(),
                                    request.fecha(),
                                    request.idMembresia(),
                                    request.idVenta(),
                                    idUsuarioRegistro
                            ));
                        }))
                        .map(ingreso -> ResponseEntity.status(HttpStatus.CREATED).body(ingreso)));
    }

    private Mono<JwtPrincipal> getJwtPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .cast(JwtPrincipal.class);
    }
}
