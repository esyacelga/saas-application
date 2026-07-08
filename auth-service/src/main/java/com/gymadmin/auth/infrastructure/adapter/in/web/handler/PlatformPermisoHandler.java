package com.gymadmin.auth.infrastructure.adapter.in.web.handler;

import com.gymadmin.auth.domain.port.in.PlatformPermisoUseCase;
import com.gymadmin.auth.dto.request.CreatePermisoRequest;
import com.gymadmin.auth.dto.request.UpdatePermisoRequest;
import com.gymadmin.auth.infrastructure.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Tag(name = "Platform - Permisos", description = "Permisos de la plataforma SaaS")
public class PlatformPermisoHandler {

    private final PlatformPermisoUseCase platformPermisoUseCase;
    private final RequestValidator validator;

    @Operation(summary = "Listar todos los permisos de la plataforma", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de permisos de plataforma"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    public Mono<ServerResponse> listar(ServerRequest request) {
        return SecurityUtils.requirePlataforma()
                .flatMap(p -> platformPermisoUseCase.listarTodos().collectList())
                .flatMap(list -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(list));
    }

    @Operation(summary = "Crear permiso de plataforma (solo superadmin)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Permiso creado"),
        @ApiResponse(responseCode = "403", description = "Requiere superadmin"),
        @ApiResponse(responseCode = "409", description = "Permiso duplicado")
    })
    public Mono<ServerResponse> crear(ServerRequest request) {
        return SecurityUtils.requireSuperAdmin()
                .flatMap(p -> request.bodyToMono(CreatePermisoRequest.class)
                        .flatMap(validator::validate)
                        .flatMap(req -> platformPermisoUseCase.crear(req, p.toIdentifier())))
                .flatMap(r -> ServerResponse.status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    @Operation(summary = "Actualizar permiso de plataforma (solo superadmin)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Permiso actualizado"),
        @ApiResponse(responseCode = "403", description = "Requiere superadmin"),
        @ApiResponse(responseCode = "404", description = "No encontrado")
    })
    public Mono<ServerResponse> actualizar(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireSuperAdmin()
                .flatMap(p -> request.bodyToMono(UpdatePermisoRequest.class)
                        .flatMap(validator::validate)
                        .flatMap(req -> platformPermisoUseCase.actualizar(id, req, p.toIdentifier())))
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    @Operation(summary = "Eliminar permiso de plataforma (solo superadmin)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Permiso eliminado"),
        @ApiResponse(responseCode = "403", description = "Requiere superadmin"),
        @ApiResponse(responseCode = "404", description = "No encontrado")
    })
    public Mono<ServerResponse> eliminar(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireSuperAdmin()
                .flatMap(p -> platformPermisoUseCase.eliminar(id, p.toIdentifier()))
                .then(ServerResponse.noContent().build());
    }
}
