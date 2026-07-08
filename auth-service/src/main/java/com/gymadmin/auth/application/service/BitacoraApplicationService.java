package com.gymadmin.auth.application.service;

import com.gymadmin.auth.domain.port.in.BitacoraUseCase;
import com.gymadmin.auth.domain.port.out.BitacoraPort;
import com.gymadmin.auth.dto.response.BitacoraPagedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class BitacoraApplicationService implements BitacoraUseCase {

    private final BitacoraPort bitacoraPort;

    @Override
    public Mono<BitacoraPagedResponse> listar(Integer idCompania, String modulo,
                                               OffsetDateTime desde, OffsetDateTime hasta,
                                               Integer idUsuario, int pagina, int limit) {
        return bitacoraPort.findWithFilters(idCompania, modulo, desde, hasta, idUsuario, pagina, limit);
    }
}
