package com.gymadmin.auth.infrastructure.adapter.in.web.handler;

import com.gymadmin.auth.domain.port.in.BitacoraUseCase;
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

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
@Tag(name = "Bitácora", description = "Registro de eventos del sistema")
public class BitacoraHandler {

    private final BitacoraUseCase bitacoraUseCase;

    @Operation(summary = "Listar entradas de la bitácora con filtros opcionales", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Registros de bitácora paginados"),
        @ApiResponse(responseCode = "403", description = "Sin permiso usuarios:leer")
    })
    public Mono<ServerResponse> listar(ServerRequest request) {
        return SecurityUtils.requireStaffWithPermiso("usuarios:leer")
                .flatMap(p -> {
                    String modulo  = request.queryParam("modulo").orElse(null);
                    OffsetDateTime desde = request.queryParam("desde")
                            .map(OffsetDateTime::parse).orElse(null);
                    OffsetDateTime hasta = request.queryParam("hasta")
                            .map(OffsetDateTime::parse).orElse(null);
                    Integer idUsuario = request.queryParam("idUsuario")
                            .map(Integer::parseInt).orElse(null);
                    int pagina = request.queryParam("pagina").map(Integer::parseInt).orElse(1);
                    int limit  = request.queryParam("limit").map(Integer::parseInt).orElse(50);
                    return bitacoraUseCase.listar(p.getIdCompania(), modulo, desde, hasta, idUsuario, pagina, limit);
                })
                .flatMap(r -> ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(r));
    }
}
