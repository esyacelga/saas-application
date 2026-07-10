package com.gymadmin.platform.application.service;

import com.gymadmin.platform.domain.model.PagoPendienteValidacion;
import com.gymadmin.platform.domain.port.in.ListarPagosPendientesUseCase;
import com.gymadmin.platform.domain.port.out.PagoPendienteValidacionRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * REQ-SAAS-001 (HU-05): bandeja de pagos pendientes para root/soporte.
 */
@Service
public class ListarPagosPendientesService implements ListarPagosPendientesUseCase {

    private final PagoPendienteValidacionRepository pagoRepository;

    public ListarPagosPendientesService(PagoPendienteValidacionRepository pagoRepository) {
        this.pagoRepository = pagoRepository;
    }

    @Override
    public Flux<PagoPendienteValidacion> listar(ListarQuery query) {
        int offset = (query.pagina() - 1) * query.limit();
        return pagoRepository.listar(query.estado(), offset, query.limit());
    }

    @Override
    public Mono<Long> contar(ListarQuery query) {
        return pagoRepository.contar(query.estado());
    }
}
