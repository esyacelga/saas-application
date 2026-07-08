package com.gymadmin.platform.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.platform.domain.model.PagoSuscripcion;
import com.gymadmin.platform.domain.port.out.PagoRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.PagoSuscripcionEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.PagoSuscripcionR2dbcRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class PagoPersistenceAdapter implements PagoRepository {

    private final PagoSuscripcionR2dbcRepository repository;

    public PagoPersistenceAdapter(PagoSuscripcionR2dbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public Flux<PagoSuscripcion> findByIdCompania(Long idCompania) {
        return repository.findByIdCompania(idCompania).map(this::toDomain);
    }

    @Override
    public Mono<PagoSuscripcion> findById(Long id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Mono<PagoSuscripcion> save(PagoSuscripcion pago) {
        PagoSuscripcionEntity entity = toEntity(pago);
        return repository.save(entity).map(this::toDomain);
    }

    @Override
    public Mono<PagoSuscripcion> update(PagoSuscripcion pago) {
        return repository.findById(pago.getId())
                .flatMap(existing -> {
                    existing.setEstado(pago.getEstado().name().toLowerCase());
                    existing.setFechaPago(pago.getFechaPago());
                    return repository.save(existing);
                })
                .map(this::toDomain);
    }

    private PagoSuscripcion toDomain(PagoSuscripcionEntity entity) {
        PagoSuscripcion p = new PagoSuscripcion();
        p.setId(entity.getId());
        p.setIdCompaniaPlan(entity.getIdCompaniaPlan());
        p.setMonto(entity.getMonto());
        p.setFechaPago(entity.getFechaPago());
        p.setPeriodoDesde(entity.getPeriodoDesde());
        p.setPeriodoHasta(entity.getPeriodoHasta());
        if (entity.getMetodoPago() != null) {
            p.setMetodoPago(PagoSuscripcion.MetodoPago.valueOf(entity.getMetodoPago().toUpperCase()));
        }
        if (entity.getTipoPago() != null) {
            p.setTipoPago(PagoSuscripcion.TipoPago.valueOf(entity.getTipoPago().toUpperCase()));
        }
        if (entity.getEstado() != null) {
            p.setEstado(PagoSuscripcion.EstadoPago.valueOf(entity.getEstado().toUpperCase()));
        }
        p.setReferencia(entity.getReferencia());
        return p;
    }

    private PagoSuscripcionEntity toEntity(PagoSuscripcion p) {
        PagoSuscripcionEntity entity = new PagoSuscripcionEntity();
        entity.setId(p.getId());
        entity.setIdCompaniaPlan(p.getIdCompaniaPlan());
        entity.setMonto(p.getMonto());
        entity.setFechaPago(p.getFechaPago());
        entity.setPeriodoDesde(p.getPeriodoDesde());
        entity.setPeriodoHasta(p.getPeriodoHasta());
        if (p.getMetodoPago() != null) {
            entity.setMetodoPago(p.getMetodoPago().name().toLowerCase());
        }
        if (p.getTipoPago() != null) {
            entity.setTipoPago(p.getTipoPago().name().toLowerCase());
        }
        if (p.getEstado() != null) {
            entity.setEstado(p.getEstado().name().toLowerCase());
        }
        entity.setReferencia(p.getReferencia());
        return entity;
    }
}
