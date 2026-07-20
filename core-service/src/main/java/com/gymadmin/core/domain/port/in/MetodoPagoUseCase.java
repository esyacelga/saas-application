package com.gymadmin.core.domain.port.in;

import com.gymadmin.core.domain.model.MetodoPago;
import reactor.core.publisher.Flux;

public interface MetodoPagoUseCase {

    Flux<MetodoPago> listarActivos(Long idCompania);
}
