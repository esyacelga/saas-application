package com.gymadmin.auth.infrastructure.adapter.in.web.handler;

import com.gymadmin.auth.domain.port.in.ResolverQrUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class GimnasioHandler {

    private final ResolverQrUseCase resolverQrUseCase;

    public Mono<ServerResponse> byQr(ServerRequest request) {
        String qrToken = request.pathVariable("qrToken");
        return resolverQrUseCase.resolverQr(qrToken)
                .flatMap(r -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(r));
    }
}
