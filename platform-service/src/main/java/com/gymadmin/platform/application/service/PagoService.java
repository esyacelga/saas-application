package com.gymadmin.platform.application.service;

import com.gymadmin.platform.domain.model.PagoSuscripcion;
import com.gymadmin.platform.domain.port.in.PagoUseCase;
import com.gymadmin.platform.domain.port.out.PagoRepository;
import com.gymadmin.platform.infrastructure.exception.NotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Service
public class PagoService implements PagoUseCase {

    private final PagoRepository pagoRepository;

    public PagoService(PagoRepository pagoRepository) {
        this.pagoRepository = pagoRepository;
    }

    @Override
    public Flux<PagoSuscripcion> getHistorialPagos(Long idCompania) {
        return pagoRepository.findByIdCompania(idCompania);
    }

    @Override
    public Mono<PagoSuscripcion> registrarPago(RegistrarPagoCommand command) {
        PagoSuscripcion pago = new PagoSuscripcion();
        pago.setIdCompaniaPlan(command.idCompaniaPlan());
        pago.setMonto(command.monto());
        pago.setFechaPago(LocalDate.now());
        pago.setPeriodoDesde(command.periodoDesde());
        pago.setPeriodoHasta(command.periodoHasta());
        pago.setMetodoPago(PagoSuscripcion.MetodoPago.valueOf(command.metodoPago().toUpperCase().replace("-","_")));
        pago.setTipoPago(PagoSuscripcion.TipoPago.valueOf(command.tipoPago().toUpperCase().replace("-","_")));
        pago.setEstado(PagoSuscripcion.EstadoPago.PENDIENTE);
        pago.setReferencia(command.referencia());
        return pagoRepository.save(pago);
    }

    @Override
    public Mono<PagoSuscripcion> confirmarPago(Long id) {
        return pagoRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Pago", id)))
                .flatMap(pago -> {
                    pago.setEstado(PagoSuscripcion.EstadoPago.PAGADO);
                    pago.setFechaPago(LocalDate.now());
                    return pagoRepository.update(pago);
                });
    }
}
