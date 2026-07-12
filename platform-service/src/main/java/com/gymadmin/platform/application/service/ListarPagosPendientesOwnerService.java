package com.gymadmin.platform.application.service;

import com.gymadmin.platform.domain.model.PagoPendienteValidacion;
import com.gymadmin.platform.domain.port.in.ListarPagosPendientesOwnerUseCase;
import com.gymadmin.platform.domain.port.out.PagoPendienteValidacionRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * REQ-SAAS-001 (Sub-fase 1.6, item #3): consulta de pagos del propio tenant
 * para la pagina "Mi suscripcion" del owner/admin.
 */
@Service
public class ListarPagosPendientesOwnerService implements ListarPagosPendientesOwnerUseCase {

    static final int DEFAULT_LIMIT = 10;
    static final int MAX_LIMIT = 50;

    private final PagoPendienteValidacionRepository pagoRepository;

    public ListarPagosPendientesOwnerService(PagoPendienteValidacionRepository pagoRepository) {
        this.pagoRepository = pagoRepository;
    }

    @Override
    public Flux<PagoPendienteValidacion> listarPorCompania(Long idCompania, int limit) {
        int effective = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return pagoRepository.listarPorCompania(idCompania, effective);
    }
}
