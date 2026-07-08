package com.gymadmin.auth.infrastructure.adapter.in.web.handler;

import com.gymadmin.auth.domain.port.in.RolUseCase;
import com.gymadmin.auth.dto.request.CreateRolRequest;
import com.gymadmin.auth.dto.request.UpdateRolPermisosRequest;
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
@Tag(name = "Roles", description = "Roles de usuarios staff")
public class RolHandler {

    private final RolUseCase rolUseCase;
    private final RequestValidator validator;

    @Operation(summary = "Listar roles de la compañía", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de roles"),
        @ApiResponse(responseCode = "403", description = "Sin permiso roles:leer")
    })
    public Mono<ServerResponse> listar(ServerRequest request) {
        return SecurityUtils.requireStaffWithPermiso("roles:leer")
                .flatMap(p -> rolUseCase.listarPorCompania(p.getIdCompania()).collectList())
                .flatMap(list -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(list));
    }

    @Operation(summary = "Crear rol de staff", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Rol creado"),
        @ApiResponse(responseCode = "403", description = "Sin permiso roles:crear"),
        @ApiResponse(responseCode = "409", description = "Nombre de rol duplicado")
    })
    public Mono<ServerResponse> crear(ServerRequest request) {
        return SecurityUtils.requireStaffWithPermiso("roles:crear")
                .flatMap(p -> request.bodyToMono(CreateRolRequest.class)
                        .flatMap(validator::validate)
                        .flatMap(req -> rolUseCase.crear(
                                TenantResolver.idCompania(req.idCompania(), p),
                                TenantResolver.idSucursal(req.idSucursal(), p),
                                req, p.toIdentifier())))
                .flatMap(r -> ServerResponse.status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    @Operation(summary = "Obtener rol por ID", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Rol encontrado"),
        @ApiResponse(responseCode = "404", description = "No encontrado")
    })
    public Mono<ServerResponse> buscarPorId(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireStaffWithPermiso("roles:leer")
                .flatMap(p -> rolUseCase.buscarPorId(id, p.getIdCompania()))
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    @Operation(summary = "Ver permisos asignados a un rol", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Permisos del rol"),
        @ApiResponse(responseCode = "403", description = "Sin permiso roles:leer")
    })
    public Mono<ServerResponse> verPermisos(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireStaffWithPermiso("roles:leer")
                .flatMap(p -> rolUseCase.verPermisos(id, p.getIdCompania()))
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    @Operation(summary = "Reemplazar permisos de un rol", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Permisos actualizados"),
        @ApiResponse(responseCode = "403", description = "Sin permiso roles:crear")
    })
    public Mono<ServerResponse> actualizarPermisos(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireStaffWithPermiso("roles:crear")
                .flatMap(p -> request.bodyToMono(UpdateRolPermisosRequest.class)
                        .flatMap(validator::validate)
                        .flatMap(req -> rolUseCase.actualizarPermisos(id, p.getIdCompania(), req, p.toIdentifier())))
                .then(ServerResponse.ok().build());
    }

    @Operation(summary = "Eliminar rol de staff", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Rol eliminado"),
        @ApiResponse(responseCode = "403", description = "Sin permiso roles:crear"),
        @ApiResponse(responseCode = "404", description = "No encontrado")
    })
    public Mono<ServerResponse> eliminar(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireStaffWithPermiso("roles:crear")
                .flatMap(p -> rolUseCase.eliminar(id, p.getIdCompania()))
                .then(ServerResponse.noContent().build());
    }
}
