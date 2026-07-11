package com.gymadmin.platform.application.service;

import com.gymadmin.platform.domain.exception.SuscripcionActivaException;
import com.gymadmin.platform.domain.exception.TrialYaUsadoException;
import com.gymadmin.platform.domain.model.ActividadPlataforma.TipoActor;
import com.gymadmin.platform.domain.model.Compania;
import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.model.Plan;
import com.gymadmin.platform.domain.port.in.ActividadPlataformaUseCase;
import com.gymadmin.platform.domain.port.in.ActivarTrialUseCase;
import com.gymadmin.platform.domain.port.in.EnviarNotificacionUseCase;
import com.gymadmin.platform.domain.port.in.ModuloCheckUseCase;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.domain.port.out.CompaniaRepository;
import com.gymadmin.platform.domain.port.out.PlanRepository;
import com.gymadmin.platform.infrastructure.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * REQ-SAAS-001 (RN-01): activa el Trial de 60 días de un tenant. Único e
 * irrevocable — la flag {@code trial_usado} queda TRUE de forma permanente.
 */
@Service
public class ActivarTrialService implements ActivarTrialUseCase {

    private static final Logger log = LoggerFactory.getLogger(ActivarTrialService.class);
    private static final String CODIGO_PLAN_TRIAL = "TRIAL";
    private static final int DURACION_TRIAL_DEFAULT_DIAS = 60;

    private final CompaniaRepository companiaRepository;
    private final CompaniaPlanRepository companiaPlanRepository;
    private final PlanRepository planRepository;
    private final ActividadPlataformaUseCase actividadPlataformaUseCase;
    private final ModuloCheckUseCase moduloCheckUseCase;
    private final EnviarNotificacionUseCase enviarNotificacionUseCase;
    private final Clock clock;

    public ActivarTrialService(CompaniaRepository companiaRepository,
                                CompaniaPlanRepository companiaPlanRepository,
                                PlanRepository planRepository,
                                ActividadPlataformaUseCase actividadPlataformaUseCase,
                                ModuloCheckUseCase moduloCheckUseCase,
                                EnviarNotificacionUseCase enviarNotificacionUseCase,
                                Clock clock) {
        this.companiaRepository = companiaRepository;
        this.companiaPlanRepository = companiaPlanRepository;
        this.planRepository = planRepository;
        this.actividadPlataformaUseCase = actividadPlataformaUseCase;
        this.moduloCheckUseCase = moduloCheckUseCase;
        this.enviarNotificacionUseCase = enviarNotificacionUseCase;
        this.clock = clock;
    }

    @Override
    public Mono<CompaniaPlan> activar(Long idCompania, Long idUsuarioActor) {
        return companiaRepository.findById(idCompania)
                .switchIfEmpty(Mono.error(new NotFoundException("Compania", idCompania)))
                .flatMap(this::verificarTrialNoUsado)
                .flatMap(compania -> verificarSinSuscripcionActiva(idCompania).thenReturn(compania))
                .flatMap(compania -> planRepository.findByCodigo(CODIGO_PLAN_TRIAL)
                        .switchIfEmpty(Mono.error(new NotFoundException("Plan Trial (codigo=TRIAL) no configurado")))
                        .flatMap(planTrial -> crearYPersistirTrial(compania, planTrial, idUsuarioActor)));
    }

    private Mono<Compania> verificarTrialNoUsado(Compania compania) {
        if (compania.isTrialUsado()) {
            return Mono.error(new TrialYaUsadoException("El tenant " + compania.getId()
                    + " ya usó su Trial (irrevocable, RN-01)"));
        }
        return Mono.just(compania);
    }

    private Mono<Void> verificarSinSuscripcionActiva(Long idCompania) {
        return companiaPlanRepository.findActivoByIdCompania(idCompania)
                .flatMap(existente -> Mono.<Void>error(new SuscripcionActivaException(
                        "El tenant " + idCompania + " ya tiene una suscripción "
                                + existente.getEstado() + " — no se puede activar Trial")))
                .switchIfEmpty(Mono.empty());
    }

    private Mono<CompaniaPlan> crearYPersistirTrial(Compania compania, Plan planTrial, Long idUsuarioActor) {
        LocalDate hoy = LocalDate.now(clock);
        int duracion = planTrial.getDuracionDias() != null ? planTrial.getDuracionDias() : DURACION_TRIAL_DEFAULT_DIAS;
        LocalDate fechaFin = hoy.plusDays(duracion);

        CompaniaPlan trial = new CompaniaPlan();
        trial.setIdCompania(compania.getId());
        trial.setIdPlan(planTrial.getId());
        trial.setFechaInicio(hoy);
        trial.setFechaFin(fechaFin);
        trial.setDiasGracia(0);
        trial.setEstado(CompaniaPlan.Estado.ACTIVO);
        trial.setTipoCambio(CompaniaPlan.TipoCambio.NUEVO);
        trial.setCreditoMonto(BigDecimal.ZERO);

        return companiaPlanRepository.save(trial)
                .flatMap(saved -> marcarTrialUsado(compania)
                        .then(registrarEventoTrialActivado(compania.getId(), idUsuarioActor, hoy, fechaFin))
                        .then(moduloCheckUseCase.invalidateCacheByCompania(compania.getId())
                                .onErrorResume(err -> {
                                    log.warn("No se pudo invalidar cache tras activar Trial: {}", err.getMessage());
                                    return Mono.just(0L);
                                }))
                        .then(encolarEmailTrialActivado(compania.getId(), saved.getId()))
                        .thenReturn(saved));
    }

    /**
     * REQ-SAAS-001 Sub-fase 1.6: encola el email {@code TRIAL_ACTIVADO} al owner.
     * El fallo del encolado no debe romper la activación del trial (fire-and-forget con log).
     */
    private Mono<Void> encolarEmailTrialActivado(Long idCompania, Long idCompaniaPlan) {
        return enviarNotificacionUseCase.encolar(new EnviarNotificacionUseCase.EncolarNotificacionCommand(
                        idCompania,
                        idCompaniaPlan,
                        "TRIAL_ACTIVADO",
                        null,
                        "email",
                        "trial_activado",
                        null,
                        null))
                .doOnError(err -> log.warn("No se pudo encolar email TRIAL_ACTIVADO (compania={}): {}",
                        idCompania, err.getMessage()))
                .onErrorResume(err -> Mono.empty())
                .then();
    }

    private Mono<Compania> marcarTrialUsado(Compania compania) {
        compania.setTrialUsado(true);
        compania.setFechaTrialUsado(Instant.now(clock));
        return companiaRepository.update(compania);
    }

    private Mono<Void> registrarEventoTrialActivado(Long idCompania, Long idUsuarioActor,
                                                     LocalDate fechaInicio, LocalDate fechaFin) {
        return actividadPlataformaUseCase.registrar(new ActividadPlataformaUseCase.RegistrarActividadCommand(
                "TRIAL_ACTIVADO",
                TipoActor.OWNER,
                idUsuarioActor,
                null,
                idCompania,
                Map.of(
                        "fecha_inicio", fechaInicio.toString(),
                        "fecha_fin", fechaFin.toString()
                )
        ));
    }
}
