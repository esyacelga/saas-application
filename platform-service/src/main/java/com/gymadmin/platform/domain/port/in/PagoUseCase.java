package com.gymadmin.platform.domain.port.in;

import com.gymadmin.platform.domain.model.PagoSuscripcion;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface PagoUseCase {

    Flux<PagoSuscripcion> getHistorialPagos(Long idCompania);

    Mono<PagoSuscripcion> registrarPago(RegistrarPagoCommand command);

    Mono<PagoSuscripcion> confirmarPago(Long id);

    record RegistrarPagoCommand(
            Long idCompaniaPlan,
            BigDecimal monto,
            String metodoPago,
            String tipoPago,
            String referencia,
            java.time.LocalDate periodoDesde,
            java.time.LocalDate periodoHasta
    ) {}
}
