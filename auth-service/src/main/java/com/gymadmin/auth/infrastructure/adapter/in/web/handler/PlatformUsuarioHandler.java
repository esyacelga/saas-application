package com.gymadmin.auth.infrastructure.adapter.in.web.handler;

import com.gymadmin.auth.application.service.CloudinaryService;
import com.gymadmin.auth.domain.port.in.PlatformUsuarioUseCase;
import com.gymadmin.auth.dto.request.CreatePlatformUsuarioRequest;
import com.gymadmin.auth.dto.request.UpdatePlatformUsuarioRequest;
import com.gymadmin.auth.infrastructure.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Tag(name = "Platform - Usuarios", description = "Usuarios de la plataforma SaaS")
public class PlatformUsuarioHandler {

    private final PlatformUsuarioUseCase platformUseCase;
    private final CloudinaryService cloudinaryService;
    private final RequestValidator validator;

    @Operation(summary = "Listar todos los usuarios de la plataforma SaaS", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de usuarios plataforma"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    public Mono<ServerResponse> listar(ServerRequest request) {
        return SecurityUtils.requirePlataforma()
                .flatMap(p -> platformUseCase.listar().collectList())
                .flatMap(list -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(list));
    }

    @Operation(summary = "Crear usuario de la plataforma SaaS", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Usuario creado"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "409", description = "Correo ya registrado")
    })
    public Mono<ServerResponse> crear(ServerRequest request) {
        return SecurityUtils.requirePlataforma()
                .flatMap(p -> request.bodyToMono(CreatePlatformUsuarioRequest.class)
                        .flatMap(validator::validate)
                        .flatMap(req -> platformUseCase.crear(req, p.toIdentifier())))
                .flatMap(r -> ServerResponse.status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    @Operation(summary = "Editar rol de usuario de la plataforma", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuario actualizado"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado"),
        @ApiResponse(responseCode = "404", description = "No encontrado")
    })
    public Mono<ServerResponse> editar(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requirePlataforma()
                .flatMap(p -> request.bodyToMono(UpdatePlatformUsuarioRequest.class)
                        .flatMap(validator::validate)
                        .flatMap(req -> platformUseCase.actualizar(id, req.rol(), p.toIdentifier())))
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    @Operation(summary = "Actualizar foto de usuario de la plataforma (multipart/form-data)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Foto actualizada"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    public Mono<ServerResponse> actualizarFoto(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requirePlataforma()
                .flatMap(p -> request.multipartData()
                        .map(parts -> (FilePart) parts.toSingleValueMap().get("file"))
                        .flatMap(filePart -> DataBufferUtils.join(filePart.content())
                                .map(buf -> {
                                    byte[] bytes = new byte[buf.readableByteCount()];
                                    buf.read(bytes);
                                    DataBufferUtils.release(buf);
                                    return bytes;
                                }))
                        .flatMap(bytes -> cloudinaryService.subirFotoOperador(bytes, id))
                        .flatMap(fotoUrl -> platformUseCase.actualizarFoto(id, fotoUrl, p.toIdentifier())))
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    @Operation(summary = "Desactivar usuario de la plataforma", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Usuario desactivado"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    public Mono<ServerResponse> desactivar(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requirePlataforma()
                .flatMap(p -> platformUseCase.desactivar(id, p.toIdentifier()))
                .then(ServerResponse.ok().build());
    }

    @Operation(summary = "Listar usuarios plataforma vinculados a una persona", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de usuarios plataforma de la persona"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
    public Mono<ServerResponse> listarPorPersona(ServerRequest request) {
        Integer idPersona = Integer.parseInt(request.pathVariable("idPersona"));
        return SecurityUtils.requirePlataforma()
                .flatMap(p -> platformUseCase.listarPorPersona(idPersona).collectList())
                .flatMap(list -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(list));
    }
}
