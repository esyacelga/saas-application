package com.gymadmin.platform.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.platform.domain.model.PagoPendienteValidacion;
import com.gymadmin.platform.domain.port.out.PagoPendienteValidacionRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.PagoPendienteValidacionEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.PagoPendienteValidacionR2dbcRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Persistence adapter para {@code tenant.pagos_pendientes_validacion} (RN-08).
 * <p>
 * Convención de enum (decisión D4): en DB {@code estado} vive en <b>minúsculas</b>
 * ({@code pendiente / aprobado / rechazado}); en Java se representa como
 * {@link PagoPendienteValidacion.Estado} en MAYÚSCULAS. El mapeo se hace aquí.
 */
@Component
public class PagoPendienteValidacionPersistenceAdapter implements PagoPendienteValidacionRepository {

    private final PagoPendienteValidacionR2dbcRepository repository;

    public PagoPendienteValidacionPersistenceAdapter(PagoPendienteValidacionR2dbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<PagoPendienteValidacion> save(PagoPendienteValidacion pago) {
        return repository.save(toEntity(pago)).map(this::toDomain);
    }

    @Override
    public Mono<PagoPendienteValidacion> findById(Long id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Mono<PagoPendienteValidacion> findByHashIdempotencia(String hash) {
        return repository.findByHashIdempotencia(hash).map(this::toDomain);
    }

    private PagoPendienteValidacion toDomain(PagoPendienteValidacionEntity entity) {
        PagoPendienteValidacion p = new PagoPendienteValidacion();
        p.setId(entity.getId());
        p.setIdCompania(entity.getIdCompania());
        p.setIdPlanDestino(entity.getIdPlanDestino());
        p.setMonto(entity.getMonto());
        p.setMoneda(entity.getMoneda());
        if (entity.getFechaReporte() != null) {
            p.setFechaReporte(entity.getFechaReporte().toInstant());
        }
        p.setFechaTransferencia(entity.getFechaTransferencia());
        p.setComprobanteUrl(entity.getComprobanteUrl());
        p.setComprobanteHash(entity.getComprobanteHash());
        p.setBancoOrigen(entity.getBancoOrigen());
        p.setReferencia(entity.getReferencia());
        p.setHashIdempotencia(entity.getHashIdempotencia());
        if (entity.getEstado() != null) {
            p.setEstado(PagoPendienteValidacion.Estado.valueOf(entity.getEstado().toUpperCase()));
        }
        p.setMotivoRechazo(entity.getMotivoRechazo());
        p.setAprobadoPor(entity.getAprobadoPor());
        if (entity.getFechaAprobacion() != null) {
            p.setFechaAprobacion(entity.getFechaAprobacion().toInstant());
        }
        p.setActivacionProgramada(Boolean.TRUE.equals(entity.getActivacionProgramada()));
        p.setFacturaEmitidaId(entity.getFacturaEmitidaId());
        p.setEliminado(entity.getEliminado());
        p.setCreacionFecha(entity.getCreacionFecha());
        p.setCreacionUsuario(entity.getCreacionUsuario());
        p.setModificaFecha(entity.getModificaFecha());
        p.setModificaUsuario(entity.getModificaUsuario());
        return p;
    }

    private PagoPendienteValidacionEntity toEntity(PagoPendienteValidacion p) {
        PagoPendienteValidacionEntity entity = new PagoPendienteValidacionEntity();
        entity.setId(p.getId());
        entity.setIdCompania(p.getIdCompania());
        entity.setIdPlanDestino(p.getIdPlanDestino());
        entity.setMonto(p.getMonto());
        entity.setMoneda(p.getMoneda());
        if (p.getFechaReporte() != null) {
            entity.setFechaReporte(OffsetDateTime.ofInstant(p.getFechaReporte(), ZoneOffset.UTC));
        }
        entity.setFechaTransferencia(p.getFechaTransferencia());
        entity.setComprobanteUrl(p.getComprobanteUrl());
        entity.setComprobanteHash(p.getComprobanteHash());
        entity.setBancoOrigen(p.getBancoOrigen());
        entity.setReferencia(p.getReferencia());
        entity.setHashIdempotencia(p.getHashIdempotencia());
        if (p.getEstado() != null) {
            entity.setEstado(p.getEstado().name().toLowerCase());
        }
        entity.setMotivoRechazo(p.getMotivoRechazo());
        entity.setAprobadoPor(p.getAprobadoPor());
        if (p.getFechaAprobacion() != null) {
            entity.setFechaAprobacion(OffsetDateTime.ofInstant(p.getFechaAprobacion(), ZoneOffset.UTC));
        }
        entity.setActivacionProgramada(p.isActivacionProgramada());
        entity.setFacturaEmitidaId(p.getFacturaEmitidaId());
        return entity;
    }
}
