package com.gymadmin.platform.infrastructure.scheduler;

import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.model.NotificacionSuscripcion;
import com.gymadmin.platform.domain.model.Plan;
import com.gymadmin.platform.domain.port.in.EnviarNotificacionUseCase;
import com.gymadmin.platform.domain.port.in.EnviarNotificacionUseCase.EncolarNotificacionCommand;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.domain.port.out.NotificacionRepository;
import com.gymadmin.platform.domain.port.out.PlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * REQ-SAAS-001 (Sub-fase 1.5): job diario que genera notificaciones de vencimiento.
 * <p>
 * Corre 10 minutos después del {@code SubscriptionJobService} para no pisarlo.
 * Para cada suscripción ACTIVA con {@code fecha_fin} dentro de los próximos 15 días,
 * evalúa los buckets {15, 7, 3, 1, 0} y encola una notificación por canal (EMAIL y
 * BANNER) — el predicado idempotente en el repositorio evita duplicados.
 */
@Component
public class NotificacionVencimientoJob {

    private static final Logger log = LoggerFactory.getLogger(NotificacionVencimientoJob.class);
    private static final List<Integer> BUCKETS = List.of(15, 7, 3, 1, 0);
    private static final String CODIGO_PLAN_TRIAL = "TRIAL";
    private static final String CODIGO_PLAN_PREMIUM = "PREMIUM";

    private final CompaniaPlanRepository companiaPlanRepository;
    private final PlanRepository planRepository;
    private final NotificacionRepository notificacionRepository;
    private final EnviarNotificacionUseCase enviarUseCase;
    private final Clock clock;

    public NotificacionVencimientoJob(CompaniaPlanRepository companiaPlanRepository,
                                       PlanRepository planRepository,
                                       NotificacionRepository notificacionRepository,
                                       EnviarNotificacionUseCase enviarUseCase,
                                       Clock clock) {
        this.companiaPlanRepository = companiaPlanRepository;
        this.planRepository = planRepository;
        this.notificacionRepository = notificacionRepository;
        this.enviarUseCase = enviarUseCase;
        this.clock = clock;
    }

    @Scheduled(cron = "${notificacion.vencimiento.cron:0 15 3 * * *}")
    public void ejecutar() {
        LocalDate today = LocalDate.now(clock);
        log.info("Iniciando job de notificaciones de vencimiento — hoy={}", today);
        procesar(today)
                .subscribe(
                        null,
                        err -> log.error("Job de notificaciones falló", err),
                        () -> log.info("Job de notificaciones completado")
                );
    }

    /** Expuesto package-private para tests con Clock.fixed(...). */
    Mono<Void> procesar(LocalDate today) {
        return companiaPlanRepository.findActivosAndEnGracia()
                .filter(cp -> cp.getFechaFin() != null)
                .filter(cp -> ChronoUnit.DAYS.between(today, cp.getFechaFin()) <= 15L)
                .flatMap(cp -> planRepository.findById(cp.getIdPlan())
                        .filter(this::esPlanNotificable)
                        .flatMapMany(plan -> generarNotificaciones(cp, plan, today)))
                .then();
    }

    private boolean esPlanNotificable(Plan plan) {
        String codigo = plan.getCodigo();
        return CODIGO_PLAN_TRIAL.equalsIgnoreCase(codigo) || CODIGO_PLAN_PREMIUM.equalsIgnoreCase(codigo);
    }

    private Flux<Long> generarNotificaciones(CompaniaPlan cp, Plan plan, LocalDate today) {
        long diasRestantes = ChronoUnit.DAYS.between(today, cp.getFechaFin());
        String tipo = CODIGO_PLAN_TRIAL.equalsIgnoreCase(plan.getCodigo())
                ? "VENCIMIENTO_TRIAL"
                : "VENCIMIENTO_PREMIUM";
        return Flux.fromIterable(BUCKETS)
                .filter(bucket -> diasRestantes <= bucket)
                .next()
                .flatMapMany(bucket -> encolarSiNoExiste(cp, plan, tipo, bucket));
    }

    private Flux<Long> encolarSiNoExiste(CompaniaPlan cp, Plan plan, String tipo, int bucket) {
        return Flux.fromIterable(List.of(NotificacionSuscripcion.CANAL_EMAIL, NotificacionSuscripcion.CANAL_BANNER))
                .flatMap(canal -> notificacionRepository
                        .existsIdempotente(cp.getId(), tipo, canal, bucket)
                        .filter(exists -> !exists)
                        .flatMap(notExists -> enviarUseCase.encolar(new EncolarNotificacionCommand(
                                cp.getIdCompania(),
                                cp.getId(),
                                tipo,
                                bucket,
                                canal,
                                templateKeyPorBucket(bucket),
                                Map.of(),
                                null
                        ))));
    }

    private static String templateKeyPorBucket(int bucket) {
        return "vencimiento_" + bucket + "d";
    }
}
