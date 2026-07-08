package com.gymadmin.auth.domain.port.out;

import com.gymadmin.auth.dto.response.GimnasioPublicoResponse;
import reactor.core.publisher.Mono;

public interface GimnasioPort {
    Mono<GimnasioPublicoResponse> findByQrToken(String qrToken);
    Mono<GimnasioPublicoResponse> findByIdCompania(Integer idCompania);
}
