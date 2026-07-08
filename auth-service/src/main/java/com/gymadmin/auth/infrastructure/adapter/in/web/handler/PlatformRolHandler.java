package com.gymadmin.auth.infrastructure.adapter.in.web.handler;

import com.gymadmin.auth.domain.port.in.PlatformRolUseCase;
import com.gymadmin.auth.domain.port.in.UsuarioStaffUseCase;
import com.gymadmin.auth.dto.request.AsignarPermisoRolRequest;
import com.gymadmin.auth.dto.request.CreatePlatformRolRequest;
import com.gymadmin.auth.dto.request.UpdatePlatformRolRequest;
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
@Tag(name = "Platform - Roles", description = "Roles y permisos de la plataforma SaaS")
public class PlatformRolHandler {

    private final PlatformRolUseCase platformRolUseCase;
    private final UsuarioStaffUseCase usuarioStaffUseCase;
    private final RequestValidator validator;

    @Operation(summary = "Listar roles de la plataforma", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de roles"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    public Mono<ServerResponse> listarRoles(ServerRequest request) {
        return SecurityUtils.requirePlataforma()
                .flatMap(p -> platformRolUseCase.listarRoles().collectList())
                .flatMap(list -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(list));
    }

    @Operation(summary = "Ver permisos de un rol de plataforma", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Permisos del rol"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    public Mono<ServerResponse> verPermisos(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requirePlataforma()
                .flatMap(p -> platformRolUseCase.verPermisosPorRol(id))
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    @Operation(summary = "Listar todas las compañías (plataforma)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de compañías"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    public Mono<ServerResponse> listarCompanias(ServerRequest request) {
        return SecurityUtils.requirePlataforma()
                .flatMap(p -> platformRolUseCase.listarCompanias().collectList())
                .flatMap(list -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(list));
    }

    @Operation(summary = "Listar sucursales de una compañía (plataforma)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de sucursales"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    public Mono<ServerResponse> listarSucursales(ServerRequest request) {
        Integer idCompania = Integer.parseInt(request.pathVariable("idCompania"));
        return SecurityUtils.requirePlataforma()
                .flatMapMany(p -> platformRolUseCase.listarSucursales(idCompania))
                .collectList()
                .flatMap(list -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(list));
    }

    @Operation(summary = "Listar usuarios staff de una compañía (plataforma)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de usuarios staff"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    public Mono<ServerResponse> listarUsuariosCompania(ServerRequest request) {
        Integer idCompania = Integer.parseInt(request.pathVariable("idCompania"));
        return SecurityUtils.requirePlataforma()
                .flatMapMany(p -> usuarioStaffUseCase.listar(idCompania))
                .collectList()
                .flatMap(list -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(list));
    }

    @Operation(summary = "Resetear contraseña de un usuario staff (plataforma)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Contraseña reseteada"),
        @ApiResponse(responseCode = "400", description = "Contraseña vacía"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    public Mono<ServerResponse> resetPasswordUsuario(ServerRequest request) {
        Integer idCompania = Integer.parseInt(request.pathVariable("idCompania"));
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requirePlataforma()
                .flatMap(p -> request.bodyToMono(java.util.Map.class)
                        .flatMap(body -> {
                            String newPassword = (String) body.get("password");
                            if (newPassword == null || newPassword.isBlank())
                                return Mono.error(new com.gymadmin.auth.domain.exception.BadRequestException("La contraseña no puede estar vacía"));
                            return usuarioStaffUseCase.resetPassword(id, idCompania, newPassword, p.toIdentifier());
                        }))
                .then(ServerResponse.ok().build());
    }

    @Operation(summary = "Crear rol de plataforma (solo superadmin)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Rol creado"),
        @ApiResponse(responseCode = "403", description = "Requiere superadmin")
    })
    public Mono<ServerResponse> crear(ServerRequest request) {
        return SecurityUtils.requireSuperAdmin()
                .flatMap(p -> request.bodyToMono(CreatePlatformRolRequest.class)
                        .flatMap(validator::validate)
                        .flatMap(req -> platformRolUseCase.crearRol(req, p.toIdentifier())))
                .flatMap(r -> ServerResponse.status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    @Operation(summary = "Actualizar rol de plataforma (solo superadmin)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Rol actualizado"),
        @ApiResponse(responseCode = "403", description = "Requiere superadmin"),
        @ApiResponse(responseCode = "404", description = "No encontrado")
    })
    public Mono<ServerResponse> actualizar(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireSuperAdmin()
                .flatMap(p -> request.bodyToMono(UpdatePlatformRolRequest.class)
                        .flatMap(validator::validate)
                        .flatMap(req -> platformRolUseCase.actualizarRol(id, req, p.toIdentifier())))
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    @Operation(summary = "Eliminar rol de plataforma (solo superadmin)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Rol eliminado"),
        @ApiResponse(responseCode = "403", description = "Requiere superadmin"),
        @ApiResponse(responseCode = "404", description = "No encontrado")
    })
    public Mono<ServerResponse> eliminar(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireSuperAdmin()
                .flatMap(p -> platformRolUseCase.eliminarRol(id))
                .then(ServerResponse.noContent().build());
    }

    @Operation(summary = "Reemplazar permisos de un rol de plataforma (solo superadmin)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Permisos actualizados"),
        @ApiResponse(responseCode = "403", description = "Requiere superadmin")
    })
    public Mono<ServerResponse> actualizarPermisos(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireSuperAdmin()
                .flatMap(p -> request.bodyToMono(UpdateRolPermisosRequest.class)
                        .flatMap(validator::validate)
                        .flatMap(req -> platformRolUseCase.reemplazarPermisos(id, req, p.toIdentifier())))
                .then(ServerResponse.ok().build());
    }

    @Operation(summary = "Ver permisos detallados de un rol de plataforma", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Permisos detallados del rol"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    public Mono<ServerResponse> verPermisosDetalle(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requirePlataforma()
                .flatMapMany(p -> platformRolUseCase.verPermisosDetalle(id))
                .collectList()
                .flatMap(list -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(list));
    }

    @Operation(summary = "Asignar un permiso individual a un rol de plataforma (solo superadmin)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Permiso asignado"),
        @ApiResponse(responseCode = "403", description = "Requiere superadmin")
    })
    public Mono<ServerResponse> asignarPermiso(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireSuperAdmin()
                .flatMap(p -> request.bodyToMono(AsignarPermisoRolRequest.class)
                        .flatMap(validator::validate)
                        .flatMap(req -> platformRolUseCase.asignarPermiso(id, req, p.toIdentifier())))
                .then(ServerResponse.status(HttpStatus.CREATED).build());
    }

    @Operation(summary = "Quitar un permiso de un rol de plataforma (solo superadmin)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Permiso removido"),
        @ApiResponse(responseCode = "403", description = "Requiere superadmin")
    })
    public Mono<ServerResponse> eliminarPermisoDeRol(ServerRequest request) {
        Integer idRol = Integer.parseInt(request.pathVariable("id"));
        Integer idPermiso = Integer.parseInt(request.pathVariable("idPermiso"));
        return SecurityUtils.requireSuperAdmin()
                .flatMap(p -> platformRolUseCase.eliminarPermisoDeRol(idRol, idPermiso, p.toIdentifier()))
                .then(ServerResponse.noContent().build());
    }
}
