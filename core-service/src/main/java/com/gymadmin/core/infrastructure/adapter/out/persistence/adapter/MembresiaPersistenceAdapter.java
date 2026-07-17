package com.gymadmin.core.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.core.domain.model.Membresia;
import com.gymadmin.core.domain.port.out.MembresiaRepository;
import com.gymadmin.core.infrastructure.adapter.out.persistence.entity.MembresiaEntity;
import com.gymadmin.core.infrastructure.adapter.out.persistence.repository.MembresiaR2dbcRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class MembresiaPersistenceAdapter implements MembresiaRepository {

    private final MembresiaR2dbcRepository repository;

    public MembresiaPersistenceAdapter(MembresiaR2dbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public Flux<Membresia> findByIdCliente(Long idCliente) {
        return repository.findByIdCliente(idCliente).map(this::toDomain);
    }

    @Override
    public Mono<Membresia> findById(Long id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Mono<Membresia> findActivaByIdClienteAndIdCompania(Long idCliente, Long idCompania) {
        return repository.findActivaByIdClienteAndIdCompania(idCliente, idCompania).map(this::toDomain);
    }

    @Override
    public Mono<Membresia> findPendienteVivaByIdCliente(Long idCliente, Long idCompania) {
        return repository.findPendienteVivaByIdCliente(idCliente, idCompania).map(this::toDomain);
    }

    @Override
    public Flux<Membresia> findPendientesPorCompania(Long idCompania) {
        return repository.findPendientesPorCompania(idCompania).map(this::toDomain);
    }

    @Override
    public Mono<Membresia> findUltimaRechazadaByIdCliente(Long idCliente, Long idCompania) {
        return repository.findUltimaRechazadaByIdCliente(idCliente, idCompania).map(this::toDomain);
    }

    @Override
    public Mono<Long> countAsistenciasByIdMembresia(Long idMembresia) {
        return repository.countAsistenciasByIdMembresia(idMembresia);
    }

    @Override
    public Mono<Membresia> save(Membresia membresia) {
        if (membresia.getId() != null) {
            return repository.findById(membresia.getId())
                    .map(existing -> mergeIntoEntity(existing, membresia))
                    .flatMap(repository::save)
                    .map(this::toDomain);
        }
        return repository.save(toEntity(membresia)).map(this::toDomain);
    }

    private MembresiaEntity mergeIntoEntity(MembresiaEntity existing, Membresia m) {
        // Campos que pueden mutar tras la creación
        existing.setFechaInicio(m.getFechaInicio());
        existing.setFechaFin(m.getFechaFin());
        if (m.getDiasAccesoTotal() != null)      existing.setDiasAccesoTotal(m.getDiasAccesoTotal());
        if (m.getPrecioPagado() != null)         existing.setPrecioPagado(m.getPrecioPagado());
        if (m.getDescuentoAplicado() != null)    existing.setDescuentoAplicado(m.getDescuentoAplicado());
        if (m.getEstado() != null)               existing.setEstado(m.getEstado().name());
        if (m.getAsistenciasPrevias() != null)   existing.setAsistenciasPrevias(m.getAsistenciasPrevias());
        if (m.getEstadoPago() != null)           existing.setEstadoPago(m.getEstadoPago().name());
        if (m.getEliminado() != null)            existing.setEliminado(m.getEliminado());
        existing.setFechaEliminacion(m.getFechaEliminacion());
        existing.setEliminadoPor(m.getEliminadoPor());
        existing.setMotivoEliminacion(m.getMotivoEliminacion() != null ? m.getMotivoEliminacion().name() : null);
        return existing;
    }

    @Override
    public Flux<Membresia> findActivasParaJob() {
        return repository.findActivasParaJob().map(this::toDomain);
    }

    private Membresia toDomain(MembresiaEntity e) {
        Membresia m = new Membresia();
        m.setId(e.getId());
        m.setIdCompania(e.getIdCompania());
        m.setIdSucursal(e.getIdSucursal());
        m.setIdCliente(e.getIdCliente());
        m.setIdTipoMembresia(e.getIdTipoMembresia());
        m.setIdMetodoPago(e.getIdMetodoPago());
        m.setIdUsuarioRegistro(e.getIdUsuarioRegistro());
        m.setFechaInicio(e.getFechaInicio());
        m.setFechaFin(e.getFechaFin());
        m.setDiasAccesoTotal(e.getDiasAccesoTotal());
        m.setPrecioPagado(e.getPrecioPagado());
        m.setDescuentoAplicado(e.getDescuentoAplicado());
        m.setEstado(e.getEstado() != null ? Membresia.Estado.valueOf(e.getEstado()) : null);
        m.setAsistenciasPrevias(e.getAsistenciasPrevias() != null ? e.getAsistenciasPrevias() : 0);
        m.setCreatedAt(e.getCreacionFecha());
        m.setEstadoPago(e.getEstadoPago() != null ? Membresia.EstadoPago.valueOf(e.getEstadoPago()) : null);
        m.setEliminado(e.getEliminado() != null ? e.getEliminado() : Boolean.FALSE);
        m.setFechaEliminacion(e.getFechaEliminacion());
        m.setEliminadoPor(e.getEliminadoPor());
        m.setMotivoEliminacion(e.getMotivoEliminacion() != null
                ? Membresia.MotivoEliminacion.valueOf(e.getMotivoEliminacion())
                : null);
        return m;
    }

    private MembresiaEntity toEntity(Membresia m) {
        return MembresiaEntity.builder()
                .id(m.getId())
                .idCompania(m.getIdCompania())
                .idSucursal(m.getIdSucursal())
                .idCliente(m.getIdCliente())
                .idTipoMembresia(m.getIdTipoMembresia())
                .idMetodoPago(m.getIdMetodoPago())
                .idUsuarioRegistro(m.getIdUsuarioRegistro())
                .fechaInicio(m.getFechaInicio())
                .fechaFin(m.getFechaFin())
                .diasAccesoTotal(m.getDiasAccesoTotal())
                .precioPagado(m.getPrecioPagado())
                .descuentoAplicado(m.getDescuentoAplicado())
                .estado(m.getEstado() != null ? m.getEstado().name() : null)
                .asistenciasPrevias(m.getAsistenciasPrevias())
                .estadoPago(m.getEstadoPago() != null ? m.getEstadoPago().name() : null)
                .fechaEliminacion(m.getFechaEliminacion())
                .eliminadoPor(m.getEliminadoPor())
                .motivoEliminacion(m.getMotivoEliminacion() != null ? m.getMotivoEliminacion().name() : null)
                .eliminado(m.getEliminado() != null ? m.getEliminado() : Boolean.FALSE)
                .creacionFecha(m.getCreatedAt())
                .build();
    }
}
