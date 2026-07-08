package com.gymadmin.auth.infrastructure.adapter.in.web.handler;

import com.gymadmin.auth.domain.port.in.UsuarioStaffUseCase;
import com.gymadmin.auth.dto.request.CreateUsuarioStaffRequest;
import com.gymadmin.auth.dto.request.UpdateUsuarioStaffRequest;
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
@Tag(name = "Usuarios Staff", description = "Usuarios del gimnasio (staff)")
public class UsuarioStaffHandler {

    private final UsuarioStaffUseCase staffUseCase;
    private final RequestValidator validator;

    @Operation(summary = "Listar usuarios staff de la compañía", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de usuarios staff"),
        @ApiResponse(responseCode = "403", description = "Sin permiso usuarios:leer")
    })
    public Mono<ServerResponse> listar(ServerRequest request) {
        return SecurityUtils.requireStaffWithPermiso("usuarios:leer")
                .flatMap(p -> staffUseCase.listar(p.getIdCompania()).collectList())
                .flatMap(list -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(list));
    }

    @Operation(summary = "Crear usuario staff", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Usuario creado"),
        @ApiResponse(responseCode = "403", description = "Sin permiso usuarios:crear"),
        @ApiResponse(responseCode = "409", description = "Correo ya registrado")
    })
    public Mono<ServerResponse> crear(ServerRequest request) {
        return SecurityUtils.requireStaffWithPermiso("usuarios:crear")
                .flatMap(p -> request.bodyToMono(CreateUsuarioStaffRequest.class)
                        .flatMap(validator::validate)
                        .flatMap(req -> staffUseCase.crear(
                                TenantResolver.idCompania(req.idCompania(), p),
                                TenantResolver.idSucursal(req.idSucursal(), p),
                                req, p.toIdentifier())))
                .flatMap(r -> ServerResponse.status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    @Operation(summary = "Editar usuario staff", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuario actualizado"),
        @ApiResponse(responseCode = "403", description = "Sin permiso usuarios:editar"),
        @ApiResponse(responseCode = "404", description = "No encontrado")
    })
    public Mono<ServerResponse> editar(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireStaffWithPermiso("usuarios:editar")
                .flatMap(p -> request.bodyToMono(UpdateUsuarioStaffRequest.class)
                        .flatMap(validator::validate)
                        .flatMap(req -> staffUseCase.editar(id, p.getIdCompania(), req, p.toIdentifier())))
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    @Operation(summary = "Ver permisos efectivos de un usuario staff", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Permisos del usuario"),
        @ApiResponse(responseCode = "403", description = "Sin permiso usuarios:leer")
    })
    public Mono<ServerResponse> verPermisos(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireStaffWithPermiso("usuarios:leer")
                .flatMap(p -> staffUseCase.verPermisos(id, p.getIdCompania()))
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    @Operation(summary = "Desactivar usuario staff", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuario desactivado"),
        @ApiResponse(responseCode = "403", description = "Sin permiso usuarios:crear")
    })
    public Mono<ServerResponse> desactivar(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireStaffWithPermiso("usuarios:crear")
                .flatMap(p -> staffUseCase.desactivar(id, p.getIdCompania(), p.toIdentifier()))
                .then(ServerResponse.ok().build());
    }

    @Operation(summary = "Activar usuario staff", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuario activado"),
        @ApiResponse(responseCode = "403", description = "Sin permiso usuarios:crear")
    })
    public Mono<ServerResponse> activar(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireStaffWithPermiso("usuarios:crear")
                .flatMap(p -> staffUseCase.activar(id, p.getIdCompania(), p.toIdentifier()))
                .then(ServerResponse.ok().build());
    }

    @Operation(summary = "Listar usuarios staff vinculados a una persona (solo plataforma)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de usuarios staff de la persona"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    public Mono<ServerResponse> listarPorPersona(ServerRequest request) {
        Integer idPersona = Integer.parseInt(request.pathVariable("idPersona"));
        return SecurityUtils.requirePlataforma()
                .flatMap(p -> staffUseCase.listarPorPersona(idPersona).collectList())
                .flatMap(list -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(list));
    }
}
