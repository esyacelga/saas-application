package com.gymadmin.core.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.core.domain.model.Congelamiento;
import com.gymadmin.core.domain.port.out.CongelamientoRepository;
import com.gymadmin.core.infrastructure.adapter.out.persistence.entity.CongelamientoEntity;
import com.gymadmin.core.infrastructure.adapter.out.persistence.repository.CongelamientoR2dbcRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class CongelamientoPersistenceAdapter implements CongelamientoRepository {

    private final CongelamientoR2dbcRepository repository;

    public CongelamientoPersistenceAdapter(CongelamientoR2dbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public Flux<Congelamiento> findByIdMembresia(Long idMembresia) {
        return repository.findByIdMembresia(idMembresia).map(this::toDomain);
    }

    @Override
    public Mono<Congelamiento> findById(Long id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Mono<Congelamiento> findActivoByIdMembresia(Long idMembresia) {
        return repository.findActivoByIdMembresia(idMembresia).map(this::toDomain);
    }

    @Override
    public Mono<Congelamiento> save(Congelamiento cong) {
        if (cong.getId() != null) {
            return repository.findById(cong.getId())
                    .map(existing -> mergeIntoEntity(existing, cong))
                    .flatMap(repository::save)
                    .map(this::toDomain);
        }
        return repository.save(toEntity(cong)).map(this::toDomain);
    }

    private CongelamientoEntity mergeIntoEntity(CongelamientoEntity existing, Congelamiento c) {
        if (c.getFechaFin() != null)          existing.setFechaFin(c.getFechaFin());
        if (c.getAprobadoPor() != null)       existing.setAprobadoPor(c.getAprobadoPor());
        if (c.getFechaAprobacion() != null)   existing.setFechaAprobacion(c.getFechaAprobacion());
        return existing;
    }

    private Congelamiento toDomain(CongelamientoEntity e) {
        Congelamiento c = new Congelamiento();
        c.setId(e.getId());
        c.setIdCompania(e.getIdCompania());
        c.setIdSucursal(e.getIdSucursal());
        c.setIdMembresia(e.getIdMembresia());
        c.setFechaInicio(e.getFechaInicio());
        c.setFechaFin(e.getFechaFin());
        c.setMotivo(e.getMotivo() != null ? Congelamiento.Motivo.valueOf(e.getMotivo()) : null);
        c.setDetalle(e.getDetalle());
        c.setRetroactivo(e.getRetroactivo());
        c.setDocumentoRespaldo(e.getDocumentoRespaldo());
        c.setAprobadoPor(e.getAprobadoPor());
        c.setFechaAprobacion(e.getFechaAprobacion());
        c.setIdUsuarioRegistro(e.getIdUsuarioRegistro());
        c.setCreatedAt(e.getCreacionFecha());
        return c;
    }

    private CongelamientoEntity toEntity(Congelamiento c) {
        return CongelamientoEntity.builder()
                .id(c.getId())
                .idCompania(c.getIdCompania())
                .idSucursal(c.getIdSucursal())
                .idMembresia(c.getIdMembresia())
                .fechaInicio(c.getFechaInicio())
                .fechaFin(c.getFechaFin())
                .motivo(c.getMotivo() != null ? c.getMotivo().name() : null)
                .detalle(c.getDetalle())
                .retroactivo(c.getRetroactivo() != null ? c.getRetroactivo() : false)
                .documentoRespaldo(c.getDocumentoRespaldo())
                .aprobadoPor(c.getAprobadoPor())
                .fechaAprobacion(c.getFechaAprobacion())
                .idUsuarioRegistro(c.getIdUsuarioRegistro())
                .creacionFecha(c.getCreatedAt())
                .build();
    }
}
