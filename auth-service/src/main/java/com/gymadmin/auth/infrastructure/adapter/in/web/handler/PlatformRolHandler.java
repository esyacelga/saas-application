package com.gymadmin.auth.infrastructure.adapter.in.web.handler;

import com.gymadmin.auth.domain.port.in.PlatformRolUseCase;
import com.gymadmin.auth.domain.port.in.UsuarioStaffUseCase;
import com.gymadmin.auth.dto.request.AsignarPermisoRolRequest;
import com.gymadmin.auth.dto.request.CreatePlatformRolRequest;
import com.gymadmin.auth.dto.request.UpdatePlatformRolRequest;
import com.gymadmin.auth.dto.request.UpdateRolPermisosRequest;
import com.gymadmin.auth.infrastructure.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class PlatformRolHandler {

    private final PlatformRolUseCase platformRolUseCase;
    private final UsuarioStaffUseCase usuarioStaffUseCase;
    private final RequestValidator validator;

    public Mono<ServerResponse> listarRoles(ServerRequest request) {
        return SecurityUtils.requirePlataforma()
                .flatMap(p -> platformRolUseCase.listarRoles().collectList())
                .flatMap(list -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(list));
    }

    public Mono<ServerResponse> verPermisos(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requirePlataforma()
                .flatMap(p -> platformRolUseCase.verPermisosPorRol(id))
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    public Mono<ServerResponse> listarCompanias(ServerRequest request) {
        return SecurityUtils.requirePlataforma()
                .flatMap(p -> platformRolUseCase.listarCompanias().collectList())
                .flatMap(list -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(list));
    }

    public Mono<ServerResponse> listarSucursales(ServerRequest request) {
        Integer idCompania = Integer.parseInt(request.pathVariable("idCompania"));
        return SecurityUtils.requirePlataforma()
                .flatMapMany(p -> platformRolUseCase.listarSucursales(idCompania))
                .collectList()
                .flatMap(list -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(list));
    }

    public Mono<ServerResponse> listarUsuariosCompania(ServerRequest request) {
        Integer idCompania = Integer.parseInt(request.pathVariable("idCompania"));
        return SecurityUtils.requirePlataforma()
                .flatMapMany(p -> usuarioStaffUseCase.listar(idCompania))
                .collectList()
                .flatMap(list -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(list));
    }

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

    public Mono<ServerResponse> crear(ServerRequest request) {
        return SecurityUtils.requireSuperAdmin()
                .flatMap(p -> request.bodyToMono(CreatePlatformRolRequest.class)
                        .flatMap(validator::validate)
                        .flatMap(req -> platformRolUseCase.crearRol(req, p.toIdentifier())))
                .flatMap(r -> ServerResponse.status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    public Mono<ServerResponse> actualizar(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireSuperAdmin()
                .flatMap(p -> request.bodyToMono(UpdatePlatformRolRequest.class)
                        .flatMap(validator::validate)
                        .flatMap(req -> platformRolUseCase.actualizarRol(id, req, p.toIdentifier())))
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    public Mono<ServerResponse> eliminar(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireSuperAdmin()
                .flatMap(p -> platformRolUseCase.eliminarRol(id))
                .then(ServerResponse.noContent().build());
    }

    public Mono<ServerResponse> actualizarPermisos(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireSuperAdmin()
                .flatMap(p -> request.bodyToMono(UpdateRolPermisosRequest.class)
                        .flatMap(validator::validate)
                        .flatMap(req -> platformRolUseCase.reemplazarPermisos(id, req, p.toIdentifier())))
                .then(ServerResponse.ok().build());
    }

    public Mono<ServerResponse> verPermisosDetalle(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requirePlataforma()
                .flatMapMany(p -> platformRolUseCase.verPermisosDetalle(id))
                .collectList()
                .flatMap(list -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(list));
    }

    public Mono<ServerResponse> asignarPermiso(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireSuperAdmin()
                .flatMap(p -> request.bodyToMono(AsignarPermisoRolRequest.class)
                        .flatMap(validator::validate)
                        .flatMap(req -> platformRolUseCase.asignarPermiso(id, req, p.toIdentifier())))
                .then(ServerResponse.status(HttpStatus.CREATED).build());
    }

    public Mono<ServerResponse> eliminarPermisoDeRol(ServerRequest request) {
        Integer idRol = Integer.parseInt(request.pathVariable("id"));
        Integer idPermiso = Integer.parseInt(request.pathVariable("idPermiso"));
        return SecurityUtils.requireSuperAdmin()
                .flatMap(p -> platformRolUseCase.eliminarPermisoDeRol(idRol, idPermiso, p.toIdentifier()))
                .then(ServerResponse.noContent().build());
    }
}
