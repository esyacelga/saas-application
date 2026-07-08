package com.gymadmin.platform.domain.port.in;

import com.gymadmin.platform.domain.model.Plan;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

public interface PlanUseCase {

    Flux<Plan> listarPlanes();

    Flux<Plan> listarPlanesPublicos();

    Mono<Plan> crearPlan(CrearPlanCommand command);

    Mono<Plan> actualizarPlan(Long id, ActualizarPlanCommand command);

    Mono<Plan> asignarCaracteristicas(Long id, List<Long> caracteristicaIds);

    Mono<Void> desactivarPlan(Long id);

    record CrearPlanCommand(
            String nombre,
            String descripcion,
            java.math.BigDecimal precioMensual
    ) {}

    record ActualizarPlanCommand(
            String nombre,
            String descripcion,
            java.math.BigDecimal precioMensual
    ) {}
}
