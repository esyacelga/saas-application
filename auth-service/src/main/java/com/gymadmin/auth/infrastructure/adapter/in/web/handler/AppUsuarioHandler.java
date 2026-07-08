package com.gymadmin.auth.infrastructure.adapter.in.web.handler;

import com.gymadmin.auth.domain.port.in.AppUsuarioUseCase;
import com.gymadmin.auth.dto.request.CreateAppUsuarioRequest;
import com.gymadmin.auth.dto.request.UpdateAppUsuarioRequest;
import com.gymadmin.auth.infrastructure.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class AppUsuarioHandler {

    private final AppUsuarioUseCase appUsuarioUseCase;
    private final RequestValidator validator;

    public Mono<ServerResponse> crear(ServerRequest request) {
        return SecurityUtils.requireStaff()
                .flatMap(p -> request.bodyToMono(CreateAppUsuarioRequest.class)
                        .flatMap(validator::validate)
                        .flatMap(req -> appUsuarioUseCase.crear(
                                TenantResolver.idCompania(req.idCompania(), p),
                                req, p.toIdentifier())))
                .then(ServerResponse.status(HttpStatus.CREATED).build());
    }

    public Mono<ServerResponse> activar(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireStaff()
                .flatMap(p -> appUsuarioUseCase.activar(id, p.toIdentifier()))
                .then(ServerResponse.ok().build());
    }

    public Mono<ServerResponse> desactivar(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireStaff()
                .flatMap(p -> appUsuarioUseCase.desactivar(id, p.toIdentifier()))
                .then(ServerResponse.ok().build());
    }

    public Mono<ServerResponse> obtenerPorCi(ServerRequest request) {
        String ci = request.pathVariable("ci");
        return SecurityUtils.requireStaff()
                .flatMap(p -> appUsuarioUseCase.obtenerPorCi(ci, p.getIdCompania()))
                .flatMap(res -> ServerResponse.ok().bodyValue(res))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    public Mono<ServerResponse> actualizar(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireStaff()
                .flatMap(p -> request.bodyToMono(UpdateAppUsuarioRequest.class)
                        .flatMap(req -> appUsuarioUseCase.actualizar(id, req, p.toIdentifier())))
                .then(ServerResponse.ok().build());
    }

    public Mono<ServerResponse> listarPorPersona(ServerRequest request) {
        Integer idPersona = Integer.parseInt(request.pathVariable("idPersona"));
        return SecurityUtils.requirePlataforma()
                .flatMap(p -> appUsuarioUseCase.listarPorPersona(idPersona).collectList())
                .flatMap(list -> ServerResponse.ok()
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).bodyValue(list));
    }
}
