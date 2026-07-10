package com.gymadmin.platform.application.service;

import com.gymadmin.platform.domain.exception.SinSuscripcionCancelableException;
import com.gymadmin.platform.domain.model.ActividadPlataforma.TipoActor;
import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.model.Plan;
import com.gymadmin.platform.domain.port.in.ActividadPlataformaUseCase;
import com.gymadmin.platform.domain.port.in.CancelarSuscripcionUseCase;
import com.gymadmin.platform.domain.port.in.ModuloCheckUseCase;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.domain.port.out.PlanRepository;
import com.gymadmin.platform.infrastructure.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * REQ-SAAS-001 (RN-09): cancelación voluntaria de la suscripción activa.
 * <p>
 * Comportamiento por plan:
 * <ul>
 *   <li>Free permanente / sin suscripción activa → {@link SinSuscripcionCancelableException}.</li>
 *   <li>Trial → transiciona a CANCELADO inmediato + nuevo plan Free ACTIVO. {@code trial_usado} NO se libera.</li>
 *   <li>Premium (u otro pago) → NO degrada de inmediato. Solo registra la cancelación en actividad;
 *       el job diario degradará al vencer.</li>
 * </ul>
 * TODO: RN-09 requiere flag {@code cancelacion_pendiente} en {@code tenant.compania_planes} para
 * distinguir "premium con cancelación pedida" de "premium normal". Cuando exista la columna,
 * marcarla aquí y hacer que el job la respete.
 */
@Service
public class CancelarSuscripcionService implements CancelarSuscripcionUseCase {

    private static final Logger log = LoggerFactory.getLogger(CancelarSuscripcionService.class);
    private static final String CODIGO_PLAN_TRIAL = "TRIAL";
    private static final String CODIGO_PLAN_FREE = "FREE";

    private final CompaniaPlanRepository companiaPlanRepository;
    private final PlanRepository planRepository;
    private final ActividadPlataformaUseCase actividadPlataformaUseCase;
    private final ModuloCheckUseCase moduloCheckUseCase;
    private final Clock clock;

    public CancelarSuscripcionService(CompaniaPlanRepository companiaPlanRepository,
                                       PlanRepository planRepository,
                                       ActividadPlataformaUseCase actividadPlataformaUseCase,
                                       ModuloCheckUseCase moduloCheckUseCase,
                                       Clock clock) {
        this.companiaPlanRepository = companiaPlanRepository;
        this.planRepository = planRepository;
        this.actividadPlataformaUseCase = actividadPlataformaUseCase;
        this.moduloCheckUseCase = moduloCheckUseCase;
        this.clock = clock;
    }

    @Override
    public Mono<Void> cancelar(Long idCompania, Long idUsuarioActor, String motivoOpcional) {
        return companiaPlanRepository.findActivoByIdCompania(idCompania)
                .switchIfEmpty(Mono.error(new SinSuscripcionCancelableException(
                        "El tenant " + idCompania + " no tiene suscripción cancelable (Free permanente)")))
                .flatMap(actual -> planRepository.findById(actual.getIdPlan())
                        .switchIfEmpty(Mono.error(new NotFoundException("Plan", actual.getIdPlan())))
                        .flatMap(plan -> ejecutarCancelacion(idCompania, idUsuarioActor, motivoOpcional, actual, plan)))
                .then(Mono.defer(() -> moduloCheckUseCase.invalidateCacheByCompania(idCompania)
                        .onErrorResume(err -> {
                            log.warn("No se pudo invalidar cache tras cancelar: {}", err.getMessage());
                            return Mono.just(0L);
                        })))
                .then();
    }

    private Mono<Void> ejecutarCancelacion(Long idCompania,
                                            Long idUsuarioActor,
                                            String motivoOpcional,
                                            CompaniaPlan actual,
                                            Plan plan) {
        boolean esTrial = CODIGO_PLAN_TRIAL.equalsIgnoreCase(plan.getCodigo());
        if (esTrial) {
            return cancelarTrialInmediato(idCompania, idUsuarioActor, motivoOpcional, actual);
        }
        // TODO: RN-09 requiere flag cancelacion_pendiente en tenant.compania_planes.
        // Por ahora se registra la cancelación como evento y el job diario degrada al vencer.
        return registrarEventoCancelacion(idCompania, idUsuarioActor, actual.getId(), motivoOpcional, "PLAN_PAGO");
    }

    private Mono<Void> cancelarTrialInmediato(Long idCompania,
                                               Long idUsuarioActor,
                                               String motivoOpcional,
                                               CompaniaPlan trialActual) {
        LocalDate hoy = LocalDate.now(clock);
        trialActual.setEstado(CompaniaPlan.Estado.CANCELADO);
        trialActual.setTipoCambio(CompaniaPlan.TipoCambio.CANCELACION);
        trialActual.setFechaFin(hoy);
        trialActual.setMotivoSuspension(motivoOpcional);

        return companiaPlanRepository.save(trialActual)
                .flatMap(saved -> planRepository.findByCodigo(CODIGO_PLAN_FREE)
                        .switchIfEmpty(Mono.error(new NotFoundException("Plan Free (codigo=FREE) no configurado")))
                        .flatMap(planFree -> {
                            CompaniaPlan free = new CompaniaPlan();
                            free.setIdCompania(idCompania);
                            free.setIdPlan(planFree.getId());
                            free.setFechaInicio(hoy);
                            free.setFechaFin(hoy.plusYears(100));
                            free.setDiasGracia(0);
                            free.setEstado(CompaniaPlan.Estado.ACTIVO);
                            free.setTipoCambio(CompaniaPlan.TipoCambio.CANCELACION);
                            free.setIdCompaniaPlanOrig(saved.getId());
                            free.setCreditoMonto(BigDecimal.ZERO);
                            return companiaPlanRepository.save(free);
                        }))
                .then(registrarEventoCancelacion(idCompania, idUsuarioActor, trialActual.getId(), motivoOpcional, "TRIAL"));
    }

    private Mono<Void> registrarEventoCancelacion(Long idCompania,
                                                   Long idUsuarioActor,
                                                   Long idCompaniaPlan,
                                                   String motivoOpcional,
                                                   String contexto) {
        Map<String, Object> detalle = new HashMap<>();
        detalle.put("id_compania_plan", idCompaniaPlan);
        detalle.put("motivo_owner", motivoOpcional != null ? motivoOpcional : "");
        detalle.put("contexto", contexto);
        return actividadPlataformaUseCase.registrar(new ActividadPlataformaUseCase.RegistrarActividadCommand(
                "SUSCRIPCION_CANCELADA",
                TipoActor.OWNER,
                idUsuarioActor,
                null,
                idCompania,
                detalle
        ));
    }
}
