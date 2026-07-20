package com.gymadmin.core.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.core.domain.model.MetodoPago;
import com.gymadmin.core.domain.port.out.MetodoPagoRepository;
import com.gymadmin.core.infrastructure.adapter.out.persistence.entity.MetodoPagoEntity;
import com.gymadmin.core.infrastructure.adapter.out.persistence.repository.MetodoPagoR2dbcRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class MetodoPagoPersistenceAdapter implements MetodoPagoRepository {

    private final MetodoPagoR2dbcRepository repository;

    public MetodoPagoPersistenceAdapter(MetodoPagoR2dbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public Flux<MetodoPago> findByIdCompaniaAndActivoTrueAndEliminadoFalse(Long idCompania) {
        return repository.findByIdCompaniaAndActivoTrueAndEliminadoFalse(idCompania).map(this::toDomain);
    }

    private MetodoPago toDomain(MetodoPagoEntity e) {
        MetodoPago m = new MetodoPago();
        m.setId(e.getId());
        m.setIdCompania(e.getIdCompania());
        m.setIdSucursal(e.getIdSucursal());
        m.setNombre(e.getNombre());
        m.setActivo(e.getActivo());
        m.setEliminado(e.getEliminado());
        return m;
    }
}
