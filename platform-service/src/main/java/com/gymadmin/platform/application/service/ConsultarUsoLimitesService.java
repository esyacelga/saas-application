package com.gymadmin.platform.application.service;

import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.model.Plan;
import com.gymadmin.platform.domain.model.RecursoLimitable;
import com.gymadmin.platform.domain.port.in.ConsultarUsoLimitesUseCase;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.domain.port.out.PlanRepository;
import com.gymadmin.platform.infrastructure.exception.NotFoundException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * REQ-SAAS-001 (RN-06, HU-04): reporte de uso actual vs límites del plan.
 */
@Service
public class ConsultarUsoLimitesService implements ConsultarUsoLimitesUseCase {

    private static final String CODIGO_TRIAL = "TRIAL";

    private final CompaniaPlanRepository companiaPlanRepository;
    private final PlanRepository planRepository;
    private final LimiteRecursoService limiteRecursoService;
    private final Clock clock;

    public ConsultarUsoLimitesService(CompaniaPlanRepository companiaPlanRepository,
                                       PlanRepository planRepository,
                                       LimiteRecursoService limiteRecursoService,
                                       Clock clock) {
        this.companiaPlanRepository = companiaPlanRepository;
        this.planRepository = planRepository;
        this.limiteRecursoService = limiteRecursoService;
        this.clock = clock;
    }

    @Override
    public Mono<UsoLimitesResult> consultar(Long idCompania) {
        return companiaPlanRepository.findActivoByIdCompania(idCompania)
                .switchIfEmpty(Mono.error(new NotFoundException(
                        "No hay suscripción activa para la compañía " + idCompania)))
                .flatMap(cp -> planRepository.findById(cp.getIdPlan())
                        .switchIfEmpty(Mono.error(new NotFoundException("Plan", cp.getIdPlan())))
                        .flatMap(plan -> construirResultado(idCompania, cp, plan)));
    }

    private Mono<UsoLimitesResult> construirResultado(Long idCompania, CompaniaPlan cp, Plan plan) {
        Mono<Long> sucMono = limiteRecursoService.contarUsoActual(idCompania, RecursoLimitable.SUCURSALES);
        Mono<Long> cliMono = limiteRecursoService.contarUsoActual(idCompania, RecursoLimitable.CLIENTES_ACTIVOS);
        Mono<Long> staffMono = limiteRecursoService.contarUsoActual(idCompania, RecursoLimitable.STAFF);

        return Mono.zip(sucMono, cliMono, staffMono)
                .map(t -> new UsoLimitesResult(
                        plan.getCodigo(),
                        new UsoRecurso(t.getT1(), toLong(plan.getMaxSucursales())),
                        new UsoRecurso(t.getT2(), toLong(plan.getMaxClientesActivos())),
                        new UsoRecurso(t.getT3(), toLong(plan.getMaxStaff())),
                        cp.isSobreLimite(),
                        cp.getSobreLimiteHasta(),
                        calcularDiasRestantes(plan, cp)
                ));
    }

    private Integer calcularDiasRestantes(Plan plan, CompaniaPlan cp) {
        if (!CODIGO_TRIAL.equals(plan.getCodigo()) || cp.getFechaFin() == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(LocalDate.now(clock), cp.getFechaFin());
    }

    private Long toLong(Integer valor) {
        return valor == null ? null : valor.longValue();
    }
}
