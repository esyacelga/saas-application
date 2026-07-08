package com.gymadmin.platform.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.CompaniaPlanEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.CompaniaPlanR2dbcRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Component
public class CompaniaPlanPersistenceAdapter implements CompaniaPlanRepository {

    private final CompaniaPlanR2dbcRepository repository;

    public CompaniaPlanPersistenceAdapter(CompaniaPlanR2dbcRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<CompaniaPlan> findById(Long id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Mono<CompaniaPlan> findActivoByIdCompania(Long idCompania) {
        return repository.findActivoByIdCompania(idCompania).map(this::toDomain);
    }

    @Override
    public Flux<CompaniaPlan> findHistorialByIdCompania(Long idCompania) {
        return repository.findByIdCompania(idCompania).map(this::toDomain);
    }

    @Override
    public Mono<CompaniaPlan> save(CompaniaPlan companiaPlan) {
        CompaniaPlanEntity entity = toEntity(companiaPlan);
        return repository.save(entity).map(this::toDomain);
    }

    @Override
    public Flux<CompaniaPlan> findByEstado(String estado) {
        return repository.findByEstado(estado).map(this::toDomain);
    }

    @Override
    public Flux<CompaniaPlan> findActivosVencidos(LocalDate today) {
        return repository.findActivosVencidos(today).map(this::toDomain);
    }

    @Override
    public Flux<CompaniaPlan> findEnGraciaVencidos(LocalDate today) {
        return repository.findEnGraciaVencidos(today).map(this::toDomain);
    }

    @Override
    public Flux<CompaniaPlan> findProgramadosParaActivar(LocalDate today) {
        return repository.findProgramadosParaActivar(today).map(this::toDomain);
    }

    @Override
    public Flux<CompaniaPlan> findActivosAndEnGracia() {
        return repository.findActivosAndEnGracia().map(this::toDomain);
    }

    @Override
    public Mono<Void> updateEstadoById(Long id, String estado, String motivo) {
        return repository.updateEstadoById(id, estado, motivo);
    }

    private CompaniaPlan toDomain(CompaniaPlanEntity entity) {
        CompaniaPlan cp = new CompaniaPlan();
        cp.setId(entity.getId());
        cp.setIdCompania(entity.getIdCompania());
        cp.setIdPlan(entity.getIdPlan());
        cp.setFechaInicio(entity.getFechaInicio());
        cp.setFechaFin(entity.getFechaFin());
        cp.setDiasGracia(entity.getDiasGracia());
        cp.setFechaUltimoPago(entity.getFechaUltimoPago());
        cp.setMotivoSuspension(entity.getMotivoSuspension());
        if (entity.getEstado() != null) {
            cp.setEstado(CompaniaPlan.Estado.valueOf(entity.getEstado().toUpperCase()));
        }
        if (entity.getTipoCambio() != null) {
            cp.setTipoCambio(CompaniaPlan.TipoCambio.valueOf(entity.getTipoCambio().toUpperCase()));
        }
        cp.setIdCompaniaPlanOrig(entity.getIdCompaniaPlanOrig());
        cp.setCreditoMonto(entity.getCreditoMonto());
        return cp;
    }

    private CompaniaPlanEntity toEntity(CompaniaPlan cp) {
        CompaniaPlanEntity entity = new CompaniaPlanEntity();
        entity.setId(cp.getId());
        entity.setIdCompania(cp.getIdCompania());
        entity.setIdPlan(cp.getIdPlan());
        entity.setFechaInicio(cp.getFechaInicio());
        entity.setFechaFin(cp.getFechaFin());
        entity.setDiasGracia(cp.getDiasGracia());
        entity.setFechaUltimoPago(cp.getFechaUltimoPago());
        entity.setMotivoSuspension(cp.getMotivoSuspension());
        if (cp.getEstado() != null) {
            entity.setEstado(cp.getEstado().name().toLowerCase());
        }
        if (cp.getTipoCambio() != null) {
            entity.setTipoCambio(cp.getTipoCambio().name().toLowerCase());
        }
        entity.setIdCompaniaPlanOrig(cp.getIdCompaniaPlanOrig());
        entity.setCreditoMonto(cp.getCreditoMonto());
        return entity;
    }
}
