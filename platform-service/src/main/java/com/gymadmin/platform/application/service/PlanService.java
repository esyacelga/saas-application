package com.gymadmin.platform.application.service;

import com.gymadmin.platform.domain.model.Plan;
import com.gymadmin.platform.domain.port.in.PlanUseCase;
import com.gymadmin.platform.domain.port.out.PlanRepository;
import com.gymadmin.platform.infrastructure.exception.NotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class PlanService implements PlanUseCase {

    private final PlanRepository planRepository;

    public PlanService(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Override
    public Flux<Plan> listarPlanes() {
        return planRepository.findAllWithCaracteristicas();
    }

    @Override
    public Flux<Plan> listarPlanesPublicos() {
        return planRepository.findAllWithCaracteristicas()
                .filter(plan -> Boolean.TRUE.equals(plan.getActivo()));
    }

    @Override
    public Mono<Plan> crearPlan(CrearPlanCommand command) {
        Plan plan = new Plan();
        plan.setNombre(command.nombre());
        plan.setDescripcion(command.descripcion());
        plan.setPrecioMensual(command.precioMensual());
        plan.setActivo(true);
        return planRepository.save(plan);
    }

    @Override
    public Mono<Plan> actualizarPlan(Long id, ActualizarPlanCommand command) {
        return planRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Plan", id)))
                .flatMap(plan -> {
                    plan.setNombre(command.nombre());
                    plan.setDescripcion(command.descripcion());
                    plan.setPrecioMensual(command.precioMensual());
                    return planRepository.update(plan);
                });
    }

    @Override
    public Mono<Plan> asignarCaracteristicas(Long id, List<Long> caracteristicaIds) {
        return planRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Plan", id)))
                .flatMap(plan ->
                        planRepository.deleteCaracteristicasByPlanId(id)
                                .then(planRepository.saveCaracteristicaRelations(id, caracteristicaIds))
                                .then(planRepository.findByIdWithCaracteristicas(id))
                );
    }

    @Override
    public Mono<Void> desactivarPlan(Long id) {
        return planRepository.findById(id)
                .switchIfEmpty(Mono.error(new NotFoundException("Plan", id)))
                .flatMap(plan -> {
                    plan.setActivo(false);
                    return planRepository.update(plan);
                })
                .then();
    }
}
