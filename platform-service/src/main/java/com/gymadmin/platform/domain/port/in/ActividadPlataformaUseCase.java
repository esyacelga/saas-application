package com.gymadmin.platform.domain.port.in;

import com.gymadmin.platform.domain.model.ActividadPlataforma;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ActividadPlataformaUseCase {

    Flux<ActividadPlataforma> listar(ListarQuery query);

    Mono<Long> contar(ListarQuery query);

    Mono<Void> registrar(RegistrarCommand command);

    record ListarQuery(
            String modulo,
            String tipoEvento,
            String desde,
            String hasta,
            int pagina,
            int porPagina
    ) {}

    record RegistrarCommand(
            String tipoEvento,
            String modulo,
            Long entidadId,
            String entidadNombre,
            String detalle,
            String usuario
    ) {}
}
