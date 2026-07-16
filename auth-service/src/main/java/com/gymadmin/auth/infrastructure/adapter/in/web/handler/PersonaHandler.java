package com.gymadmin.auth.infrastructure.adapter.in.web.handler;

import com.gymadmin.auth.application.service.CloudinaryService;
import com.gymadmin.auth.domain.port.in.PersonaUseCase;
import com.gymadmin.auth.dto.request.ConsentimientoWaRequest;
import com.gymadmin.auth.dto.request.CreatePersonaRequest;
import com.gymadmin.auth.dto.request.UpdatePersonaRequest;
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
@Tag(name = "Personas", description = "Gestión de personas físicas")
public class PersonaHandler {

    private final PersonaUseCase personaUseCase;
    private final RequestValidator validator;
    private final CloudinaryService cloudinaryService;

    @Operation(summary = "Listar personas con filtros opcionales (solo plataforma)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista paginada de personas"),
        @ApiResponse(responseCode = "403", description = "Acceso denegado")
    })
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

    @Operation(summary = "Obtener persona por ID", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Persona encontrada"),
        @ApiResponse(responseCode = "404", description = "No encontrada")
    })
    public Mono<ServerResponse> findById(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return personaUseCase.findById(id)
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    @Operation(summary = "Obtener persona por cédula de identidad", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Persona encontrada"),
        @ApiResponse(responseCode = "404", description = "No encontrada")
    })
    public Mono<ServerResponse> findByCi(ServerRequest request) {
        String ci = request.pathVariable("ci");
        return personaUseCase.findByCi(ci)
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    @Operation(summary = "Obtener persona por correo electrónico", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Persona encontrada"),
        @ApiResponse(responseCode = "404", description = "No encontrada")
    })
    public Mono<ServerResponse> findByCorreo(ServerRequest request) {
        String correo = request.pathVariable("correo");
        return personaUseCase.findByCorreo(correo)
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    @Operation(summary = "Crear nueva persona", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Persona creada"),
        @ApiResponse(responseCode = "409", description = "CI o correo ya registrado")
    })
    public Mono<ServerResponse> crear(ServerRequest request) {
        return SecurityUtils.currentUserIdentifier()
                .flatMap(identity -> request.bodyToMono(CreatePersonaRequest.class)
                        .flatMap(validator::validate)
                        .flatMap(req -> personaUseCase.create(req, identity)))
                .flatMap(r -> ServerResponse.status(HttpStatus.CREATED)
                        .contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    @Operation(summary = "Actualizar datos de una persona", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Persona actualizada"),
        @ApiResponse(responseCode = "404", description = "No encontrada")
    })
    public Mono<ServerResponse> actualizar(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        return SecurityUtils.currentUserIdentifier()
                .flatMap(identity -> request.bodyToMono(UpdatePersonaRequest.class)
                        .flatMap(req -> personaUseCase.update(id, req, identity)))
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    @Operation(summary = "Registrar opt-in/opt-out de WhatsApp del socio", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Consentimiento actualizado"),
        @ApiResponse(responseCode = "400", description = "Body inválido (acepta requerido)"),
        @ApiResponse(responseCode = "404", description = "Persona no encontrada")
    })
    public Mono<ServerResponse> actualizarConsentimientoWa(ServerRequest request) {
        Integer id = Integer.parseInt(request.pathVariable("id"));
        // currentUserIdentifier() exige un JWT válido (staff/cliente/plataforma): cubre los tres puntos
        // de captura del socio (registro/recepción/perfil PWA) sin acoplar a un rol concreto.
        return SecurityUtils.currentUserIdentifier()
                .flatMap(identity -> request.bodyToMono(ConsentimientoWaRequest.class)
                        .flatMap(validator::validate)
                        .flatMap(req -> personaUseCase.actualizarConsentimientoWa(id, req.acepta())))
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }

    @Operation(summary = "Subir foto de perfil de una persona (multipart/form-data)", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Foto actualizada"),
        @ApiResponse(responseCode = "404", description = "Persona no encontrada")
    })
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