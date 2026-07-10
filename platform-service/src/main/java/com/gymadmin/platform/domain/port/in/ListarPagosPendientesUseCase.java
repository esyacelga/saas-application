package com.gymadmin.platform.domain.port.in;

import com.gymadmin.platform.domain.model.PagoPendienteValidacion;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REQ-SAAS-001 (RN-08, HU-05): bandeja de pagos pendientes visible para
 * operadores root/soporte.
 */
public interface ListarPagosPendientesUseCase {

    Flux<PagoPendienteValidacion> listar(ListarQuery query);

    Mono<Long> contar(ListarQuery query);

    record ListarQuery(String estado, int pagina, int limit) {
        public ListarQuery {
            if (pagina < 1) pagina = 1;
            if (limit < 1) limit = 20;
            if (limit > 200) limit = 200;
        }
    }
}
