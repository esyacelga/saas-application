package com.gymadmin.auth.infrastructure.adapter.in.web.handler;

import com.gymadmin.auth.domain.port.in.RolUseCase;
import com.gymadmin.auth.dto.request.CreateRolRequest;
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
public class RolHandler {

    private final RolUseCase rolUseCase;
    private final RequestValidator validator;

    public Mono<ServerResponse> listar(ServerRequest request) {
        return SecurityUtils.requireStaffWithPermiso("roles:leer")
                .flatMap(p -> rolUseCase.listarPorCompania(p.getIdCompania()).collectList())
                .flatMap(list -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(list));
    }

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

    public Mono<ServerResponse> buscarPorId(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireStaffWithPermiso("roles:leer")
                .flatMap(p -> rolUseCase.buscarPorId(id, p.getIdCompania()))
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    public Mono<ServerResponse> verPermisos(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireStaffWithPermiso("roles:leer")
                .flatMap(p -> rolUseCase.verPermisos(id, p.getIdCompania()))
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    public Mono<ServerResponse> actualizarPermisos(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireStaffWithPermiso("roles:crear")
                .flatMap(p -> request.bodyToMono(UpdateRolPermisosRequest.class)
                        .flatMap(validator::validate)
                        .flatMap(req -> rolUseCase.actualizarPermisos(id, p.getIdCompania(), req, p.toIdentifier())))
                .then(ServerResponse.ok().build());
    }

    public Mono<ServerResponse> eliminar(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireStaffWithPermiso("roles:crear")
                .flatMap(p -> rolUseCase.eliminar(id, p.getIdCompania()))
                .then(ServerResponse.noContent().build());
    }
}
