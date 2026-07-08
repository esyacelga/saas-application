package com.gymadmin.core.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.core.domain.model.TipoMembresia;
import com.gymadmin.core.domain.port.out.TipoMembresiaRepository;
import com.gymadmin.core.infrastructure.adapter.out.persistence.entity.TipoMembresiaEntity;
import com.gymadmin.core.infrastructure.adapter.out.persistence.repository.TipoMembresiaR2dbcRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class TipoMembresiaPersistenceAdapter implements TipoMembresiaRepository {

    private final TipoMembresiaR2dbcRepository repository;

    public TipoMembresiaPersistenceAdapter(TipoMembresiaR2dbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public Flux<TipoMembresia> findActivosByIdCompania(Long idCompania) {
        return repository.findActivosByIdCompania(idCompania).map(this::toDomain);
    }

    @Override
    public Mono<TipoMembresia> findById(Long id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Mono<TipoMembresia> findByNombreAndIdCompania(String nombre, Long idCompania) {
        return repository.findByNombreAndIdCompania(nombre, idCompania).map(this::toDomain);
    }

    @Override
    public Mono<Boolean> existeMembresiaActivaDeEsteTipo(Long idTipoMembresia) {
        return repository.existeMembresiaActivaDeEsteTipo(idTipoMembresia);
    }

    @Override
    public Mono<TipoMembresia> save(TipoMembresia tipo) {
        if (tipo.getId() != null) {
            return repository.findById(tipo.getId())
                    .map(existing -> mergeIntoEntity(existing, tipo))
                    .flatMap(repository::save)
                    .map(this::toDomain);
        }
        return repository.save(toEntity(tipo)).map(this::toDomain);
    }

    private TipoMembresiaEntity mergeIntoEntity(TipoMembresiaEntity existing, TipoMembresia t) {
        if (t.getNombre() != null)      existing.setNombre(t.getNombre());
        if (t.getModoControl() != null) existing.setModoControl(t.getModoControl().name());
        if (t.getDuracionTipo() != null) existing.setDuracionTipo(t.getDuracionTipo().name());
        if (t.getDuracionValor() != null) existing.setDuracionValor(t.getDuracionValor());
        if (t.getPrecio() != null)      existing.setPrecio(t.getPrecio());
        if (t.getActivo() != null)      existing.setActivo(t.getActivo());
        existing.setDiasAcceso(t.getDiasAcceso());
        return existing;
    }

    private TipoMembresia toDomain(TipoMembresiaEntity e) {
        TipoMembresia t = new TipoMembresia();
        t.setId(e.getId());
        t.setIdCompania(e.getIdCompania());
        t.setIdSucursal(e.getIdSucursal());
        t.setNombre(e.getNombre());
        t.setModoControl(e.getModoControl() != null ? TipoMembresia.ModoControl.valueOf(e.getModoControl()) : null);
        t.setDuracionTipo(e.getDuracionTipo() != null ? TipoMembresia.DuracionTipo.valueOf(e.getDuracionTipo()) : null);
        t.setDuracionValor(e.getDuracionValor());
        t.setDiasAcceso(e.getDiasAcceso());
        t.setPrecio(e.getPrecio());
        t.setActivo(e.getActivo());
        return t;
    }

    private TipoMembresiaEntity toEntity(TipoMembresia t) {
        return TipoMembresiaEntity.builder()
                .id(t.getId())
                .idCompania(t.getIdCompania())
                .idSucursal(t.getIdSucursal())
                .nombre(t.getNombre())
                .modoControl(t.getModoControl() != null ? t.getModoControl().name() : null)
                .duracionTipo(t.getDuracionTipo() != null ? t.getDuracionTipo().name() : null)
                .duracionValor(t.getDuracionValor())
                .diasAcceso(t.getDiasAcceso())
                .precio(t.getPrecio())
                .activo(t.getActivo())
                .build();
    }
}
