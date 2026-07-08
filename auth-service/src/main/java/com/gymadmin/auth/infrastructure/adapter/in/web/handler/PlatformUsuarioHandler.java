package com.gymadmin.auth.infrastructure.adapter.in.web.handler;

import com.gymadmin.auth.application.service.CloudinaryService;
import com.gymadmin.auth.domain.port.in.PlatformUsuarioUseCase;
import com.gymadmin.auth.dto.request.CreatePlatformUsuarioRequest;
import com.gymadmin.auth.dto.request.UpdatePlatformUsuarioRequest;
import com.gymadmin.auth.infrastructure.security.SecurityUtils;
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
public class PlatformUsuarioHandler {

    private final PlatformUsuarioUseCase platformUseCase;
    private final CloudinaryService cloudinaryService;
    private final RequestValidator validator;

    public Mono<ServerResponse> listar(ServerRequest request) {
        return SecurityUtils.requirePlataforma()
                .flatMap(p -> platformUseCase.listar().collectList())
                .flatMap(list -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(list));
    }

    public Mono<ServerResponse> crear(ServerRequest request) {
        return SecurityUtils.requirePlataforma()
                .flatMap(p -> request.bodyToMono(CreatePlatformUsuarioRequest.class)
                        .flatMap(validator::validate)
                        .flatMap(req -> platformUseCase.crear(req, p.toIdentifier())))
                .flatMap(r -> ServerResponse.status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    public Mono<ServerResponse> editar(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requirePlataforma()
                .flatMap(p -> request.bodyToMono(UpdatePlatformUsuarioRequest.class)
                        .flatMap(validator::validate)
                        .flatMap(req -> platformUseCase.actualizar(id, req.rol(), p.toIdentifier())))
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

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

    public Mono<ServerResponse> desactivar(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.requirePlataforma()
                .flatMap(p -> platformUseCase.desactivar(id, p.toIdentifier()))
                .then(ServerResponse.ok().build());
    }

    public Mono<ServerResponse> listarPorPersona(ServerRequest request) {
        Integer idPersona = Integer.parseInt(request.pathVariable("idPersona"));
        return SecurityUtils.requirePlataforma()
                .flatMap(p -> platformUseCase.listarPorPersona(idPersona).collectList())
                .flatMap(list -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(list));
    }
}
