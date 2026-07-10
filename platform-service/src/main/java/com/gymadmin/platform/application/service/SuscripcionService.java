package com.gymadmin.platform.application.service;

import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.model.Plan;
import com.gymadmin.platform.domain.port.in.SuscripcionUseCase;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.domain.port.out.PlanRepository;
import com.gymadmin.platform.infrastructure.exception.BusinessException;
import com.gymadmin.platform.infrastructure.exception.NotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class SuscripcionService implements SuscripcionUseCase {

    private final CompaniaPlanRepository companiaPlanRepository;
    private final PlanRepository planRepository;

    public SuscripcionService(CompaniaPlanRepository companiaPlanRepository,
                               PlanRepository planRepository) {
        this.companiaPlanRepository = companiaPlanRepository;
        this.planRepository = planRepository;
    }

    @Override
    public Mono<CompaniaPlan> getSuscripcionActiva(Long idCompania) {
        return companiaPlanRepository.findHistorialByIdCompania(idCompania)
                .filter(cp -> cp.getEstado() == CompaniaPlan.Estado.ACTIVO
                        || cp.getEstado() == CompaniaPlan.Estado.EN_GRACIA
                        || cp.getEstado() == CompaniaPlan.Estado.SUSPENDIDO)
                .next()
                .switchIfEmpty(Mono.error(new NotFoundException("Subscription for company " + idCompania)));
    }

    @Override
    public Flux<CompaniaPlan> getHistorial(Long idCompania) {
        return companiaPlanRepository.findHistorialByIdCompania(idCompania);
    }

    @Override
    public Mono<CompaniaPlan> renovar(Long idCompania, RenovarCommand command) {
        return companiaPlanRepository.findActivoByIdCompania(idCompania)
                .switchIfEmpty(Mono.error(new NotFoundException("Active subscription for company " + idCompania)))
                .flatMap(currentPlan -> {
                    Long targetPlanId = command.idPlan() != null ? command.idPlan() : currentPlan.getIdPlan();
                    return planRepository.findById(targetPlanId)
                            .switchIfEmpty(Mono.error(new NotFoundException("Plan", targetPlanId)))
                            .flatMap(plan -> {
                                int meses = command.meses() != null ? command.meses() : 1;
                                CompaniaPlan renovacion = new CompaniaPlan();
                                renovacion.setIdCompania(idCompania);
                                renovacion.setIdPlan(targetPlanId);
                                renovacion.setFechaInicio(currentPlan.getFechaFin().plusDays(1));
                                renovacion.setFechaFin(currentPlan.getFechaFin().plusMonths(meses));
                                renovacion.setDiasGracia(currentPlan.getDiasGracia());
                                renovacion.setEstado(CompaniaPlan.Estado.ACTIVO);
                                renovacion.setTipoCambio(CompaniaPlan.TipoCambio.RENOVACION);
                                renovacion.setIdCompaniaPlanOrig(currentPlan.getId());
                                // RN-10: solo puede existir una fila vigente (activo/en_gracia) por
                                // compañía (índice ux_compania_plan_vigente). La fila actual pasa a
                                // REEMPLAZADA antes de insertar la nueva ACTIVA.
                                return companiaPlanRepository.updateEstadoById(
                                                currentPlan.getId(),
                                                CompaniaPlan.Estado.REEMPLAZADA.name().toLowerCase(),
                                                null)
                                        .then(companiaPlanRepository.save(renovacion));
                            });
                });
    }

    @Override
    public Mono<UpgradeResult> upgrade(Long idCompania, UpgradeCommand command) {
        return companiaPlanRepository.findActivoByIdCompania(idCompania)
                .switchIfEmpty(Mono.error(new NotFoundException("Active subscription for company " + idCompania)))
                .flatMap(currentPlan ->
                        planRepository.findById(currentPlan.getIdPlan())
                                .zipWith(planRepository.findById(command.idPlanNuevo())
                                        .switchIfEmpty(Mono.error(new NotFoundException("Plan", command.idPlanNuevo()))))
                                .flatMap(tuple -> {
                                    Plan planActual = tuple.getT1();
                                    Plan planNuevo = tuple.getT2();

                                    if (planNuevo.getPrecioMensual().compareTo(planActual.getPrecioMensual()) <= 0) {
                                        return Mono.error(new BusinessException("Upgrade requires a plan with higher price"));
                                    }

                                    LocalDate today = LocalDate.now();
                                    long diasRestantes = ChronoUnit.DAYS.between(today, currentPlan.getFechaFin());
                                    long diasTotales = ChronoUnit.DAYS.between(currentPlan.getFechaInicio(), currentPlan.getFechaFin());

                                    BigDecimal creditoProporcional = BigDecimal.ZERO;
                                    if (diasTotales > 0 && diasRestantes > 0) {
                                        creditoProporcional = planActual.getPrecioMensual()
                                                .multiply(BigDecimal.valueOf(diasRestantes))
                                                .divide(BigDecimal.valueOf(diasTotales), 2, RoundingMode.HALF_UP);
                                    }

                                    BigDecimal montoAPagar = planNuevo.getPrecioMensual().subtract(creditoProporcional);
                                    if (montoAPagar.compareTo(BigDecimal.ZERO) < 0) {
                                        montoAPagar = BigDecimal.ZERO;
                                    }

                                    BigDecimal finalMontoAPagar = montoAPagar;
                                    BigDecimal finalCreditoProporcional = creditoProporcional;

                                    // RN-10: la fila vigente actual se CANCELA (UPDATE sobre la fila
                                    // existente, no una fila nueva) antes de insertar la nueva ACTIVA,
                                    // para no violar ux_compania_plan_vigente.
                                    return companiaPlanRepository.updateEstadoById(
                                                    currentPlan.getId(),
                                                    CompaniaPlan.Estado.CANCELADO.name().toLowerCase(),
                                                    null)
                                            .then(Mono.defer(() -> {
                                                CompaniaPlan nuevo = new CompaniaPlan();
                                                nuevo.setIdCompania(idCompania);
                                                nuevo.setIdPlan(command.idPlanNuevo());
                                                nuevo.setFechaInicio(today);
                                                nuevo.setFechaFin(today.plusMonths(1));
                                                nuevo.setDiasGracia(7);
                                                nuevo.setEstado(CompaniaPlan.Estado.ACTIVO);
                                                nuevo.setTipoCambio(CompaniaPlan.TipoCambio.UPGRADE);
                                                nuevo.setIdCompaniaPlanOrig(currentPlan.getId());
                                                return companiaPlanRepository.save(nuevo)
                                                        .map(savedNuevo -> new UpgradeResult(
                                                                savedNuevo.getId(),
                                                                finalCreditoProporcional,
                                                                finalMontoAPagar,
                                                                true
                                                        ));
                                            }));
                                })
                );
    }

    @Override
    public Mono<DowngradeResult> downgrade(Long idCompania, DowngradeCommand command) {
        return companiaPlanRepository.findActivoByIdCompania(idCompania)
                .switchIfEmpty(Mono.error(new NotFoundException("Active subscription for company " + idCompania)))
                .flatMap(currentPlan ->
                        planRepository.findById(currentPlan.getIdPlan())
                                .zipWith(planRepository.findById(command.idPlanNuevo())
                                        .switchIfEmpty(Mono.error(new NotFoundException("Plan", command.idPlanNuevo()))))
                                .flatMap(tuple -> {
                                    Plan planActual = tuple.getT1();
                                    Plan planNuevo = tuple.getT2();

                                    if (planNuevo.getPrecioMensual().compareTo(planActual.getPrecioMensual()) >= 0) {
                                        return Mono.error(new BusinessException("Downgrade requires a plan with lower price"));
                                    }

                                    LocalDate today = LocalDate.now();
                                    long diasRestantes = ChronoUnit.DAYS.between(today, currentPlan.getFechaFin());
                                    long diasTotales = ChronoUnit.DAYS.between(currentPlan.getFechaInicio(), currentPlan.getFechaFin());

                                    BigDecimal creditoGenerado = BigDecimal.ZERO;
                                    if (diasTotales > 0 && diasRestantes > 0) {
                                        BigDecimal diferenciaDiaria = planActual.getPrecioMensual()
                                                .subtract(planNuevo.getPrecioMensual())
                                                .divide(BigDecimal.valueOf(diasTotales), 4, RoundingMode.HALF_UP);
                                        creditoGenerado = diferenciaDiaria.multiply(BigDecimal.valueOf(diasRestantes))
                                                .setScale(2, RoundingMode.HALF_UP);
                                    }

                                    LocalDate efectivoDe = currentPlan.getFechaFin().plusDays(1);
                                    BigDecimal finalCreditoGenerado = creditoGenerado;

                                    CompaniaPlan programado = new CompaniaPlan();
                                    programado.setIdCompania(idCompania);
                                    programado.setIdPlan(command.idPlanNuevo());
                                    programado.setFechaInicio(efectivoDe);
                                    programado.setFechaFin(efectivoDe.plusMonths(1));
                                    programado.setDiasGracia(7);
                                    programado.setEstado(CompaniaPlan.Estado.PROGRAMADO);
                                    programado.setTipoCambio(CompaniaPlan.TipoCambio.DOWNGRADE);
                                    programado.setIdCompaniaPlanOrig(currentPlan.getId());
                                    programado.setCreditoMonto(creditoGenerado);

                                    return companiaPlanRepository.save(programado)
                                            .map(savedProgramado -> new DowngradeResult(
                                                    savedProgramado.getId(),
                                                    CompaniaPlan.Estado.PROGRAMADO.name(),
                                                    efectivoDe,
                                                    finalCreditoGenerado
                                            ));
                                })
                );
    }
}
