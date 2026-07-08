package com.gymadmin.auth.infrastructure.adapter.in.web.handler;

import com.gymadmin.auth.domain.port.in.ResolverQrUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Tag(name = "Gimnasio", description = "Información del gimnasio por QR")
public class GimnasioHandler {

    private final ResolverQrUseCase resolverQrUseCase;

    @Operation(summary = "Resolver información del gimnasio por token QR")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Información del gimnasio"),
        @ApiResponse(responseCode = "404", description = "QR no encontrado o expirado")
    })
    public Mono<ServerResponse> byQr(ServerRequest request) {
        String qrToken = request.pathVariable("qrToken");
        return resolverQrUseCase.resolverQr(qrToken)
                .flatMap(r -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(r));
    }
}
