package com.gymadmin.platform.application.service;

import com.gymadmin.platform.domain.model.ActividadPlataforma.TipoActor;
import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.model.ConfigNotifSuscripcion;
import com.gymadmin.platform.domain.model.NotificacionSuscripcion;
import com.gymadmin.platform.domain.model.Plan;
import com.gymadmin.platform.domain.port.in.ActividadPlataformaUseCase;
import com.gymadmin.platform.domain.port.in.ModuloCheckUseCase;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.domain.port.out.ConfigNotifRepository;
import com.gymadmin.platform.domain.port.out.NotificacionRepository;
import com.gymadmin.platform.domain.port.out.PlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * REQ-SAAS-001 (RN-03 / RN-10): job diario que gestiona el ciclo de vida de
 * suscripciones. Orden estricto:
 * <ol>
 *   <li>Activar PROGRAMADOS cuyo {@code fecha_inicio ≤ hoy} — la suscripción
 *       activa/en gracia previa pasa a REEMPLAZADA.</li>
 *   <li>Degradar suscripciones vencidas (o EN_GRACIA agotadas) al
 *       {@code plan_degradacion_id}. Si el destino es Free: transición directa
 *       (sin gracia) y detección de sobre-límite (RN-06).</li>
 *   <li>Procesar notificaciones y invalidar cache Redis de los tenants afectados.</li>
 * </ol>
 * <p>
 * Usa {@link Clock} inyectable — RN-03 exige testeo con time-travel.
 */
@Service
public class SubscriptionJobService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionJobService.class);
    private static final String CODIGO_PLAN_FREE = "FREE";

    private final CompaniaPlanRepository companiaPlanRepository;
    private final PlanRepository planRepository;
    private final ConfigNotifRepository configNotifRepository;
    private final NotificacionRepository notificacionRepository;
    private final ActividadPlataformaUseCase actividadPlataformaUseCase;
    private final ModuloCheckUseCase moduloCheckUseCase;
    private final Clock clock;

    @Value("${jobs.run-on-startup:true}")
    private boolean runOnStartup;

    public SubscriptionJobService(CompaniaPlanRepository companiaPlanRepository,
                                   PlanRepository planRepository,
                                   ConfigNotifRepository configNotifRepository,
                                   NotificacionRepository notificacionRepository,
                                   ActividadPlataformaUseCase actividadPlataformaUseCase,
                                   ModuloCheckUseCase moduloCheckUseCase,
                                   Clock clock) {
        this.companiaPlanRepository = companiaPlanRepository;
        this.planRepository = planRepository;
        this.configNotifRepository = configNotifRepository;
        this.notificacionRepository = notificacionRepository;
        this.actividadPlataformaUseCase = actividadPlataformaUseCase;
        this.moduloCheckUseCase = moduloCheckUseCase;
        this.clock = clock;
    }

    @Scheduled(cron = "${subscription.job.cron:0 5 0 * * *}")
    public void runSubscriptionJob() {
        LocalDate today = LocalDate.now(clock);
        log.info("Starting subscription job at {}", LocalDateTime.now(clock));

        procesarSuscripciones(today)
                .then(processNotifications(today))
                .subscribe(
                        null,
                        error -> log.error("Subscription job failed", error),
                        () -> log.info("Subscription job completed successfully")
                );
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ejecutarAlIniciar() {
        if (!runOnStartup) {
            log.info("[SubscriptionJob] Skip startup run (jobs.run-on-startup=false)");
            return;
        }
        log.info("[SubscriptionJob] Ejecutando al arrancar (recuperación de ventana perdida)");
        runSubscriptionJob();
    }

    /** Expuesto para tests con Clock.fixed(...). Ejecuta el orden RN-03 completo. */
    public Mono<Void> procesarSuscripciones(LocalDate today) {
        return activarProgramados(today)
                .then(degradarVencidas(today));
    }

    // ── 1) Activar PROGRAMADOS ────────────────────────────────────────────
    private Mono<Void> activarProgramados(LocalDate today) {
        return companiaPlanRepository.findProgramadosParaActivar(today)
                .flatMap(programado -> reemplazarActivoPrevio(programado.getIdCompania(), programado.getId())
                        .then(Mono.defer(() -> {
                            programado.setEstado(CompaniaPlan.Estado.ACTIVO);
                            return companiaPlanRepository.save(programado)
                                    .doOnSuccess(saved -> log.debug("Activated programado plan {}", saved.getId()));
                        }))
                        .flatMap(saved -> invalidarCache(saved.getIdCompania()).thenReturn(saved)))
                .then();
    }

    private Mono<Void> reemplazarActivoPrevio(Long idCompania, Long idProgramado) {
        return companiaPlanRepository.findActivoByIdCompania(idCompania)
                .filter(previo -> previo.getId() != null && !previo.getId().equals(idProgramado))
                .flatMap(previo -> {
                    previo.setEstado(CompaniaPlan.Estado.REEMPLAZADA);
                    return companiaPlanRepository.save(previo)
                            .doOnSuccess(s -> log.debug("Plan {} pasó a REEMPLAZADA", s.getId()));
                })
                .then();
    }

    // ── 2) Degradar vencidas ──────────────────────────────────────────────
    private Mono<Void> degradarVencidas(LocalDate today) {
        Flux<CompaniaPlan> activosVencidos = companiaPlanRepository.findActivosVencidos(today);
        Flux<CompaniaPlan> enGraciaVencidos = companiaPlanRepository.findEnGraciaVencidos(today);
        return Flux.concat(activosVencidos, enGraciaVencidos)
                .flatMap(vencida -> degradar(vencida, today))
                .then();
    }

    private Mono<Void> degradar(CompaniaPlan vencida, LocalDate today) {
        return planRepository.findById(vencida.getIdPlan())
                .flatMap(planActual -> {
                    Long destinoId = planActual.getPlanDegradacionId();
                    if (destinoId == null) {
                        vencida.setEstado(CompaniaPlan.Estado.VENCIDO);
                        return companiaPlanRepository.save(vencida).then();
                    }
                    return planRepository.findById(destinoId)
                            .flatMap(planDestino -> ejecutarDegradacion(vencida, planActual, planDestino, today));
                });
    }

    private Mono<Void> ejecutarDegradacion(CompaniaPlan vencida,
                                            Plan planActual,
                                            Plan planDestino,
                                            LocalDate today) {
        Long idCompania = vencida.getIdCompania();
        boolean destinoFree = CODIGO_PLAN_FREE.equalsIgnoreCase(planDestino.getCodigo())
                || planDestino.isEsGratuito();
        CompaniaPlan.Estado estadoFinal = destinoFree
                ? CompaniaPlan.Estado.VENCIDO
                : CompaniaPlan.Estado.EN_GRACIA;
        vencida.setEstado(estadoFinal);
        vencida.setTipoCambio(CompaniaPlan.TipoCambio.DEGRADACION_AUTO);
        vencida.setCausaDegradacion(CompaniaPlan.CausaDegradacion.VENCIMIENTO.name().toLowerCase());

        return companiaPlanRepository.save(vencida)
                .flatMap(saved -> {
                    if (!destinoFree) {
                        return registrarEventoDegradacion(idCompania, planActual, planDestino, saved.getId())
                                .then(invalidarCache(idCompania));
                    }
                    return crearFreeActivo(idCompania, planDestino, today, saved.getId())
                            .flatMap(nuevoFree -> detectarSobreLimite(nuevoFree, planDestino, idCompania)
                                    .then(registrarEventoDegradacion(idCompania, planActual, planDestino, saved.getId()))
                                    .then(invalidarCache(idCompania)));
                });
    }

    private Mono<CompaniaPlan> crearFreeActivo(Long idCompania,
                                                Plan planFree,
                                                LocalDate today,
                                                Long idOrigen) {
        CompaniaPlan free = new CompaniaPlan();
        free.setIdCompania(idCompania);
        free.setIdPlan(planFree.getId());
        free.setFechaInicio(today);
        free.setFechaFin(today.plusYears(100));
        free.setDiasGracia(0);
        free.setEstado(CompaniaPlan.Estado.ACTIVO);
        free.setTipoCambio(CompaniaPlan.TipoCambio.DEGRADACION_AUTO);
        free.setIdCompaniaPlanOrig(idOrigen);
        free.setCausaDegradacion(CompaniaPlan.CausaDegradacion.VENCIMIENTO.name().toLowerCase());
        free.setCreditoMonto(BigDecimal.ZERO);
        return companiaPlanRepository.save(free);
    }

    /**
     * REQ-SAAS-001 (RN-06): al degradar, si el uso actual excede los límites del
     * nuevo plan → marcar {@code sobre_limite=true}, {@code sobre_limite_hasta=hoy+30d}
     * y registrar evento. La detección detallada requiere HTTP cross-service
     * (core/auth) — pendiente Sub-fase 1.4. Aquí se marca únicamente si el plan
     * destino tiene algún límite duro configurado.
     */
    private Mono<Void> detectarSobreLimite(CompaniaPlan nuevoFree, Plan planDestino, Long idCompania) {
        if (!planDestino.tieneLimites()) {
            return Mono.empty();
        }
        LocalDate hoy = LocalDate.now(clock);
        nuevoFree.setSobreLimite(true);
        nuevoFree.setSobreLimiteHasta(hoy.plusDays(30));
        return companiaPlanRepository.save(nuevoFree)
                .then(actividadPlataformaUseCase.registrar(new ActividadPlataformaUseCase.RegistrarActividadCommand(
                        "SOBRE_LIMITE_DETECTADO",
                        TipoActor.SISTEMA,
                        null,
                        null,
                        idCompania,
                        Map.of(
                                "plan_destino", planDestino.getCodigo() != null ? planDestino.getCodigo() : "",
                                "sobre_limite_hasta", hoy.plusDays(30).toString()
                        )
                )));
    }

    private Mono<Void> registrarEventoDegradacion(Long idCompania, Plan planActual, Plan planDestino, Long idCompaniaPlan) {
        Map<String, Object> detalle = new HashMap<>();
        detalle.put("plan_anterior", planActual.getCodigo() != null ? planActual.getCodigo() : "");
        detalle.put("plan_nuevo", planDestino.getCodigo() != null ? planDestino.getCodigo() : "");
        detalle.put("causa", CompaniaPlan.CausaDegradacion.VENCIMIENTO.name());
        detalle.put("id_compania_plan", idCompaniaPlan);
        return actividadPlataformaUseCase.registrar(new ActividadPlataformaUseCase.RegistrarActividadCommand(
                "PLAN_DEGRADADO_AUTO",
                TipoActor.SISTEMA,
                null,
                null,
                idCompania,
                detalle
        ));
    }

    private Mono<Void> invalidarCache(Long idCompania) {
        return moduloCheckUseCase.invalidateCacheByCompania(idCompania)
                .onErrorResume(err -> {
                    log.warn("No se pudo invalidar cache para tenant {}: {}", idCompania, err.getMessage());
                    return Mono.just(0L);
                })
                .then();
    }

    // ── 3) Notificaciones (comportamiento previo, preservado) ─────────────
    private Mono<Void> processNotifications(LocalDate today) {
        return companiaPlanRepository.findActivosAndEnGracia()
                .flatMap(cp -> {
                    long diasRestantes = ChronoUnit.DAYS.between(today, cp.getFechaFin());
                    return configNotifRepository.findByIdCompania(cp.getIdCompania())
                            .filter(config -> config.getActivo() && config.getDiasAntes() == diasRestantes)
                            .flatMap(config -> notificacionRepository
                                    .existsByIdCompaniaPlanAndDiasAntes(cp.getId(), config.getDiasAntes())
                                    .filter(exists -> !exists)
                                    .flatMap(notExists -> {
                                        NotificacionSuscripcion notif = new NotificacionSuscripcion();
                                        notif.setIdCompaniaPlan(cp.getId());
                                        notif.setDiasAntes(config.getDiasAntes());
                                        notif.setCanal(config.getCanal() != null ? config.getCanal().name() : ConfigNotifSuscripcion.Canal.WHATSAPP.name());
                                        notif.setEstado("PENDIENTE");
                                        notif.setFechaEnvio(LocalDateTime.now(clock.withZone(ZoneId.systemDefault())));
                                        return notificacionRepository.save(notif);
                                    })
                            );
                })
                .then();
    }
}
