package com.gymadmin.auth.infrastructure.adapter.in.web.handler;

import com.gymadmin.auth.application.service.CloudinaryService;
import com.gymadmin.auth.domain.port.in.PersonaUseCase;
import com.gymadmin.auth.dto.request.CreatePersonaRequest;
import com.gymadmin.auth.dto.request.UpdatePersonaRequest;
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
public class PersonaHandler {

    private final PersonaUseCase personaUseCase;
    private final RequestValidator validator;
    private final CloudinaryService cloudinaryService;

    public Mono<ServerResponse> listar(ServerRequest request) {
        String nombre = request.queryParam("nombre").orElse(null);
        String ci     = request.queryParam("ci").orElse(null);
        String correo = request.queryParam("correo").orElse(null);
        String sexo   = request.queryParam("sexo").orElse(null);
        int page      = request.queryParam("page").map(Integer::parseInt).orElse(0);
        int size      = request.queryParam("size").map(Integer::parseInt).orElse(20);
        return SecurityUtils.requirePlataforma()
                .flatMap(p -> personaUseCase.listar(nombre, ci, correo, sexo, page, size))
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    public Mono<ServerResponse> findById(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return personaUseCase.findById(id)
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    public Mono<ServerResponse> findByCi(ServerRequest request) {
        String ci = request.pathVariable("ci");
        return personaUseCase.findByCi(ci)
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    public Mono<ServerResponse> findByCorreo(ServerRequest request) {
        String correo = request.pathVariable("correo");
        return personaUseCase.findByCorreo(correo)
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    public Mono<ServerResponse> crear(ServerRequest request) {
        return SecurityUtils.currentUserIdentifier()
                .flatMap(identity -> request.bodyToMono(CreatePersonaRequest.class)
                        .flatMap(validator::validate)
                        .flatMap(req -> personaUseCase.create(req, identity)))
                .flatMap(r -> ServerResponse.status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    public Mono<ServerResponse> actualizar(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.currentUserIdentifier()
                .flatMap(identity -> request.bodyToMono(UpdatePersonaRequest.class)
                        .flatMap(req -> personaUseCase.update(id, req, identity)))
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    public Mono<ServerResponse> subirFoto(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.currentUserIdentifier()
                .flatMap(identity -> request.multipartData()
                    .map(parts -> parts.toSingleValueMap().get("file"))
                    .cast(FilePart.class)
                    .flatMap(filePart -> DataBufferUtils.join(filePart.content())
                        .map(dataBuffer -> {
                            byte[] bytes = new byte[dataBuffer.readableByteCount()];
                            dataBuffer.read(bytes);
                            DataBufferUtils.release(dataBuffer);
                            return bytes;
                        })
                    )
                    .flatMap(bytes -> cloudinaryService.subirFotoMiembro(bytes, id))
                    .flatMap(url -> personaUseCase.update(id,
                        new UpdatePersonaRequest(null, null, null, url, null, null, null), identity))
                )
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }
}