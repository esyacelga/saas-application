package com.gymadmin.auth.infrastructure.adapter.in.web.handler;

import com.gymadmin.auth.domain.port.in.UsuarioStaffUseCase;
import com.gymadmin.auth.dto.request.CreateUsuarioStaffRequest;
import com.gymadmin.auth.dto.request.UpdateUsuarioStaffRequest;
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
public class UsuarioStaffHandler {

    private final UsuarioStaffUseCase staffUseCase;
    private final RequestValidator validator;

    public Mono<ServerResponse> listar(ServerRequest request) {
        return SecurityUtils.requireStaffWithPermiso("usuarios:leer")
                .flatMap(p -> staffUseCase.listar(p.getIdCompania()).collectList())
                .flatMap(list -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(list));
    }

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

    public Mono<ServerResponse> editar(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireStaffWithPermiso("usuarios:editar")
                .flatMap(p -> request.bodyToMono(UpdateUsuarioStaffRequest.class)
                        .flatMap(validator::validate)
                        .flatMap(req -> staffUseCase.editar(id, p.getIdCompania(), req, p.toIdentifier())))
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    public Mono<ServerResponse> verPermisos(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireStaffWithPermiso("usuarios:leer")
                .flatMap(p -> staffUseCase.verPermisos(id, p.getIdCompania()))
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    public Mono<ServerResponse> desactivar(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireStaffWithPermiso("usuarios:crear")
                .flatMap(p -> staffUseCase.desactivar(id, p.getIdCompania(), p.toIdentifier()))
                .then(ServerResponse.ok().build());
    }

    public Mono<ServerResponse> activar(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireStaffWithPermiso("usuarios:crear")
                .flatMap(p -> staffUseCase.activar(id, p.getIdCompania(), p.toIdentifier()))
                .then(ServerResponse.ok().build());
    }

    public Mono<ServerResponse> listarPorPersona(ServerRequest request) {
        Integer idPersona = Integer.parseInt(request.pathVariable("idPersona"));
        return SecurityUtils.requirePlataforma()
                .flatMap(p -> staffUseCase.listarPorPersona(idPersona).collectList())
                .flatMap(list -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(list));
    }
}
