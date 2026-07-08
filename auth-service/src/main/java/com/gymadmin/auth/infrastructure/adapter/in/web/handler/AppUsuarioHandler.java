package com.gymadmin.auth.infrastructure.adapter.in.web.handler;

import com.gymadmin.auth.domain.port.in.AppUsuarioUseCase;
import com.gymadmin.auth.dto.request.CreateAppUsuarioRequest;
import com.gymadmin.auth.dto.request.UpdateAppUsuarioRequest;
import com.gymadmin.auth.infrastructure.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Tag(name = "Usuarios App", description = "Usuarios de la app móvil")
public class AppUsuarioHandler {

    private final AppUsuarioUseCase appUsuarioUseCase;
    private final RequestValidator validator;

    @Operation(summary = "Crear usuario de la app móvil", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Usuario creado"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "409", description = "Correo ya registrado")
    })
    public Mono<ServerResponse> crear(ServerRequest request) {
        return SecurityUtils.requireStaff()
                .flatMap(p -> request.bodyToMono(CreateAppUsuarioRequest.class)
                        .flatMap(validator::validate)
                        .flatMap(req -> appUsuarioUseCase.crear(
                                TenantResolver.idCompania(req.idCompania(), p),
                                req, p.toIdentifier())))
                .then(ServerResponse.status(HttpStatus.CREATED).build());
    }

    @Operation(summary = "Activar usuario de la app móvil", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuario activado"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    public Mono<ServerResponse> activar(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireStaff()
                .flatMap(p -> appUsuarioUseCase.activar(id, p.toIdentifier()))
                .then(ServerResponse.ok().build());
    }

    @Operation(summary = "Desactivar usuario de la app móvil", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuario desactivado"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    public Mono<ServerResponse> desactivar(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireStaff()
                .flatMap(p -> appUsuarioUseCase.desactivar(id, p.toIdentifier()))
                .then(ServerResponse.ok().build());
    }

    @Operation(summary = "Obtener usuario de la app por cédula de identidad", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuario encontrado"),
        @ApiResponse(responseCode = "404", description = "No encontrado")
    })
    public Mono<ServerResponse> obtenerPorCi(ServerRequest request) {
        String ci = request.pathVariable("ci");
        return SecurityUtils.requireStaff()
                .flatMap(p -> appUsuarioUseCase.obtenerPorCi(ci, p.getIdCompania()))
                .flatMap(res -> ServerResponse.ok().bodyValue(res))
                .switchIfEmpty(ServerResponse.notFound().build());
    }

    @Operation(summary = "Actualizar usuario de la app móvil", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuario actualizado"),
        @ApiResponse(responseCode = "404", description = "No encontrado")
    })
    public Mono<ServerResponse> actualizar(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requireStaff()
                .flatMap(p -> request.bodyToMono(UpdateAppUsuarioRequest.class)
                        .flatMap(req -> appUsuarioUseCase.actualizar(id, req, p.toIdentifier())))
                .then(ServerResponse.ok().build());
    }

    @Operation(summary = "Listar usuarios app vinculados a una persona (solo plataforma)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de usuarios app de la persona"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    public Mono<ServerResponse> listarPorPersona(ServerRequest request) {
        Integer idPersona = Integer.parseInt(request.pathVariable("idPersona"));
        return SecurityUtils.requirePlataforma()
                .flatMap(p -> appUsuarioUseCase.listarPorPersona(idPersona).collectList())
                .flatMap(list -> ServerResponse.ok()
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).bodyValue(list));
    }
}
