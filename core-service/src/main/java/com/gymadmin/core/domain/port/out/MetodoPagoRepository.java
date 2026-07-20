package com.gymadmin.core.domain.port.out;

import com.gymadmin.core.domain.model.MetodoPago;
import reactor.core.publisher.Flux;

public interface MetodoPagoRepository {

    Flux<MetodoPago> findByIdCompaniaAndActivoTrueAndEliminadoFalse(Long idCompania);
}
