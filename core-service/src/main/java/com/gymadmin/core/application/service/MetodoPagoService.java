package com.gymadmin.core.application.service;

import com.gymadmin.core.domain.model.MetodoPago;
import com.gymadmin.core.domain.port.in.MetodoPagoUseCase;
import com.gymadmin.core.domain.port.out.MetodoPagoRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class MetodoPagoService implements MetodoPagoUseCase {

    private final MetodoPagoRepository metodoPagoRepository;

    public MetodoPagoService(MetodoPagoRepository metodoPagoRepository) {
        this.metodoPagoRepository = metodoPagoRepository;
    }

    @Override
    public Flux<MetodoPago> listarActivos(Long idCompania) {
        return metodoPagoRepository.findByIdCompaniaAndActivoTrueAndEliminadoFalse(idCompania);
    }
}
