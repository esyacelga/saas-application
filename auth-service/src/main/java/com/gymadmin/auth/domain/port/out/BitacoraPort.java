package com.gymadmin.auth.domain.port.out;

import com.gymadmin.auth.dto.response.BitacoraPagedResponse;
import reactor.core.publisher.Mono;
import java.time.OffsetDateTime;

public interface BitacoraPort {
    Mono<Void> save(Integer idCompania, Integer idSucursal, Integer idUsuario,
                    String modulo, String accion, Integer entidadId,
                    java.util.Map<String, Object> detalle, String ip);

    Mono<BitacoraPagedResponse> findWithFilters(Integer idCompania, String modulo,
                                                OffsetDateTime desde, OffsetDateTime hasta,
                                                Integer idUsuario, int pagina, int limit);
}
