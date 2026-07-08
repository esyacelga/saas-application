package com.gymadmin.attendance.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.attendance.domain.model.PlantillaMensaje;
import com.gymadmin.attendance.domain.port.out.PlantillaMensajeRepository;
import com.gymadmin.attendance.infrastructure.adapter.out.persistence.entity.PlantillaMensajeEntity;
import com.gymadmin.attendance.infrastructure.adapter.out.persistence.repository.PlantillaMensajeR2dbcRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class PlantillaMensajePersistenceAdapter implements PlantillaMensajeRepository {

    private final PlantillaMensajeR2dbcRepository repository;

    @Override
    public Flux<PlantillaMensaje> findByCompania(Integer idCompania) {
        return repository.findByCompania(idCompania).map(this::toDomain);
    }

    @Override
    public Mono<PlantillaMensaje> findById(Integer id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Mono<PlantillaMensaje> save(PlantillaMensaje plantilla) {
        return repository.save(toEntity(plantilla)).map(this::toDomain);
    }

    @Override
    public Mono<PlantillaMensaje> update(PlantillaMensaje plantilla) {
        return repository.save(toEntity(plantilla)).map(this::toDomain);
    }

    @Override
    public Mono<Long> countActivasByTipo(Integer idCompania, String tipo) {
        return repository.countActivasByTipo(idCompania, tipo);
    }

    @Override
    public Mono<PlantillaMensaje> findRandomActivaByTipo(Integer idCompania, String tipo) {
        return repository.findRandomActivaByTipo(idCompania, tipo).map(this::toDomain);
    }

    @Override
    public Mono<Void> softDelete(Integer id) {
        return repository.findById(id)
                .flatMap(e -> {
                    e.setEliminado(true);
                    return repository.save(e);
                })
                .then();
    }

    private PlantillaMensaje toDomain(PlantillaMensajeEntity e) {
        PlantillaMensaje p = new PlantillaMensaje();
        p.setId(e.getId());
        p.setIdCompania(e.getIdCompania());
        p.setIdSucursal(e.getIdSucursal());
        p.setTipo(e.getTipo());
        p.setNombre(e.getNombre());
        p.setContenido(e.getContenido());
        p.setActivo(e.getActivo());
        p.setEliminado(e.getEliminado());
        p.setCreacionFecha(e.getCreacionFecha());
        p.setCreacionUsuario(e.getCreacionUsuario());
        p.setModificaFecha(e.getModificaFecha());
        p.setModificaUsuario(e.getModificaUsuario());
        return p;
    }

    private PlantillaMensajeEntity toEntity(PlantillaMensaje p) {
        return PlantillaMensajeEntity.builder()
                .id(p.getId())
                .idCompania(p.getIdCompania())
                .idSucursal(p.getIdSucursal())
                .tipo(p.getTipo())
                .nombre(p.getNombre())
                .contenido(p.getContenido())
                .activo(p.getActivo())
                .creacionFecha(p.getCreacionFecha())
                .creacionUsuario(p.getCreacionUsuario())
                .build();
    }
}
