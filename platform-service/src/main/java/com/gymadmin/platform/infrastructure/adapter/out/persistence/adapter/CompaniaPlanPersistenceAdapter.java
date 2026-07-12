package com.gymadmin.platform.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.platform.domain.exception.EstadoInvalidoException;
import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.CompaniaPlanEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.CompaniaPlanR2dbcRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.Set;

/**
 * Persistence adapter para {@code tenant.compania_planes}.
 * <p>
 * Convención de enum (decisión D4): en DB los valores viven en <b>minúsculas</b>
 * ({@code activo}, {@code en_gracia}, {@code reemplazada}, {@code degradacion_auto}, ...)
 * pero en Java se representan en <b>MAYÚSCULAS</b> ({@code ACTIVO}, {@code EN_GRACIA}, ...).
 * La conversión se hace aquí manualmente con {@code .name().toLowerCase()} al escribir y
 * {@code Enum.valueOf(.toUpperCase())} al leer — este ya era el patrón existente para
 * {@code Estado} y {@code TipoCambio}, se mantiene y se extiende a los nuevos valores.
 * <p>
 * REQ-SAAS-001 (sección 5bis): se validan las transiciones prohibidas en {@link #save(CompaniaPlan)}.
 */
@Component
public class CompaniaPlanPersistenceAdapter implements CompaniaPlanRepository {

    /** REQ-SAAS-001 (sección 5bis): estados terminales — no admiten transiciones salientes. */
    private static final Set<CompaniaPlan.Estado> ESTADOS_TERMINALES = Set.of(
            CompaniaPlan.Estado.CANCELADO,
            CompaniaPlan.Estado.REEMPLAZADA
    );

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
        // REQ-SAAS-001 sección 5bis — validación de transiciones prohibidas.
        // Solo aplica cuando ya existe un registro previo (UPDATE); en INSERT (id null)
        // cualquier estado inicial es válido siempre que el CHECK de DB lo permita.
        if (companiaPlan.getId() == null) {
            return Mono.defer(() -> {
                CompaniaPlanEntity entity = toEntity(companiaPlan);
                return repository.save(entity).map(this::toDomain);
            });
        }

        return repository.findById(companiaPlan.getId())
                .flatMap(previous -> validateTransition(previous, companiaPlan)
                        .then(Mono.defer(() -> {
                            CompaniaPlanEntity entity = toEntity(companiaPlan);
                            // REQ-SAAS-001 Sub-fase 1.6 item #5: el modelo de dominio no
                            // transporta creacion_fecha/creacion_usuario. Al hacer UPDATE
                            // preservamos los valores originales de la fila previa para no
                            // violar la constraint NOT NULL de creacion_fecha ni perder la
                            // auditoría original.
                            entity.setCreacionFecha(previous.getCreacionFecha());
                            entity.setCreacionUsuario(previous.getCreacionUsuario());
                            entity.setEliminado(previous.getEliminado() != null ? previous.getEliminado() : Boolean.FALSE);
                            return repository.save(entity).map(this::toDomain);
                        })));
    }

    private Mono<Void> validateTransition(CompaniaPlanEntity previous, CompaniaPlan next) {
        CompaniaPlan.Estado prev = parseEstado(previous.getEstado());
        CompaniaPlan.Estado nuevo = next.getEstado();

        if (prev == null || nuevo == null || prev == nuevo) {
            return Mono.empty();
        }

        if (ESTADOS_TERMINALES.contains(prev)) {
            return Mono.error(new EstadoInvalidoException(
                    "Estado " + prev.name() + " es terminal — no admite transición a " + nuevo.name()
                            + " (compania_plan id=" + previous.getId() + ")"));
        }

        if (nuevo == CompaniaPlan.Estado.PROGRAMADO) {
            return Mono.error(new EstadoInvalidoException(
                    "Estado PROGRAMADO solo puede producirse en la creación (INSERT), no en UPDATE"
                            + " (compania_plan id=" + previous.getId() + ", estado previo=" + prev.name() + ")"));
        }

        return Mono.empty();
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
        cp.setEstado(parseEstado(entity.getEstado()));
        cp.setTipoCambio(parseTipoCambio(entity.getTipoCambio()));
        cp.setIdCompaniaPlanOrig(entity.getIdCompaniaPlanOrig());
        cp.setCreditoMonto(entity.getCreditoMonto());
        cp.setSobreLimite(Boolean.TRUE.equals(entity.getSobreLimite()));
        cp.setSobreLimiteHasta(entity.getSobreLimiteHasta());
        cp.setCausaDegradacion(entity.getCausaDegradacion());
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
        entity.setSobreLimite(cp.isSobreLimite());
        entity.setSobreLimiteHasta(cp.getSobreLimiteHasta());
        entity.setCausaDegradacion(cp.getCausaDegradacion());
        return entity;
    }

    private CompaniaPlan.Estado parseEstado(String value) {
        if (value == null) return null;
        return CompaniaPlan.Estado.valueOf(value.toUpperCase());
    }

    private CompaniaPlan.TipoCambio parseTipoCambio(String value) {
        if (value == null) return null;
        return CompaniaPlan.TipoCambio.valueOf(value.toUpperCase());
    }
}
