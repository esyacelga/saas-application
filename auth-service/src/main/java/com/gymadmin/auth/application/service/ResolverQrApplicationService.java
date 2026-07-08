package com.gymadmin.auth.application.service;

import com.gymadmin.auth.domain.exception.ResourceNotFoundException;
import com.gymadmin.auth.domain.port.in.ResolverQrUseCase;
import com.gymadmin.auth.domain.port.out.GimnasioPort;
import com.gymadmin.auth.dto.response.GimnasioPublicoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ResolverQrApplicationService implements ResolverQrUseCase {

    private final GimnasioPort gimnasioPort;

    @Override
    public Mono<GimnasioPublicoResponse> resolverQr(String qrToken) {
        return gimnasioPort.findByQrToken(qrToken)
                .switchIfEmpty(Mono.error(new ResourceNotFoundException("QR no válido o pertenece a un gimnasio inactivo")));
    }
}
