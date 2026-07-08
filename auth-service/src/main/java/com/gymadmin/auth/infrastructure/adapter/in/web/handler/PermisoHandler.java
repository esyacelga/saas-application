package com.gymadmin.auth.infrastructure.adapter.in.web.handler;

import com.gymadmin.auth.domain.port.in.PermisoUseCase;
import com.gymadmin.auth.infrastructure.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Tag(name = "Permisos", description = "Permisos del sistema")
public class PermisoHandler {

    private final PermisoUseCase permisoUseCase;

    @Operation(summary = "Listar todos los permisos disponibles para la compañía", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista de permisos"),
        @ApiResponse(responseCode = "403", description = "Sin permiso roles:leer")
    })
    public Mono<ServerResponse> listar(ServerRequest request) {
        return SecurityUtils.requireStaffWithPermiso("roles:leer")
                .flatMap(p -> permisoUseCase.listarPorCompania(p.getIdCompania()).collectList())
                .flatMap(list -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(list));
    }

    @Operation(summary = "Listar permisos asignados a un rol", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Permisos del rol"),
        @ApiResponse(responseCode = "403", description = "Sin permiso roles:leer")
    })
    public Mono<ServerResponse> porRol(ServerRequest request) {
        Integer idRol = Integer.parseInt(request.pathVariable("idRol"));
        return SecurityUtils.requireStaffWithPermiso("roles:leer")
                .flatMap(p -> permisoUseCase.listarPorRol(idRol, p.getIdCompania()).collectList())
                .flatMap(list -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(list));
    }
}
