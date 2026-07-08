package com.gymadmin.auth.domain.port.in;

import com.gymadmin.auth.dto.response.GimnasioPublicoResponse;
import reactor.core.publisher.Mono;

public interface ResolverQrUseCase {
    Mono<GimnasioPublicoResponse> resolverQr(String qrToken);
}
