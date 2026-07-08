package com.gymadmin.auth.domain.port.in;

import com.gymadmin.auth.dto.response.BitacoraPagedResponse;
import reactor.core.publisher.Mono;
import java.time.OffsetDateTime;

public interface BitacoraUseCase {
    Mono<BitacoraPagedResponse> listar(Integer idCompania, String modulo,
                                       OffsetDateTime desde, OffsetDateTime hasta,
                                       Integer idUsuario, int pagina, int limit);
}
