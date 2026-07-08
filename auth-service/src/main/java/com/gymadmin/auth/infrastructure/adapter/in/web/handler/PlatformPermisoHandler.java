package com.gymadmin.auth.infrastructure.adapter.in.web.handler;

import com.gymadmin.auth.domain.port.in.PlatformPermisoUseCase;
import com.gymadmin.auth.dto.request.CreatePermisoRequest;
import com.gymadmin.auth.dto.request.UpdatePermisoRequest;
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
public class PlatformPermisoHandler {

    private final PlatformPermisoUseCase platformPermisoUseCase;
    private final RequestValidator validator;

    public Mono<ServerResponse> listar(ServerRequest request) {
        return SecurityUtils.requirePlataforma()
                .flatMap(p -> platformPermisoUseCase.listarTodos().collectList())
                .flatMap(list -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(list));
    }

    public Mono<ServerResponse> crear(ServerRequest request) {
        return SecurityUtils.requireSuperAdmin()
                .flatMap(p -> request.bodyToMono(CreatePermisoRequest.class)
                        .flatMap(validator::validate)
                        .flatMap(req -> platformPermisoUseCase.crear(req, p.toIdentifier())))
                .flatMap(r -> ServerResponse.status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    public Mono<ServerResponse> actualizar(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireSuperAdmin()
                .flatMap(p -> request.bodyToMono(UpdatePermisoRequest.class)
                        .flatMap(validator::validate)
                        .flatMap(req -> platformPermisoUseCase.actualizar(id, req, p.toIdentifier())))
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    public Mono<ServerResponse> eliminar(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireSuperAdmin()
                .flatMap(p -> platformPermisoUseCase.eliminar(id, p.toIdentifier()))
                .then(ServerResponse.noContent().build());
    }
}
