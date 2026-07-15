package com.gymadmin.platform.infrastructure.scheduler;

import com.gymadmin.platform.domain.model.Compania;
import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.model.ConfigNotifSuscripcion;
import com.gymadmin.platform.domain.model.NotificacionSuscripcion;
import com.gymadmin.platform.domain.model.Plan;
import com.gymadmin.platform.domain.port.in.EnviarNotificacionUseCase;
import com.gymadmin.platform.domain.port.in.EnviarNotificacionUseCase.EncolarNotificacionCommand;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.domain.port.out.CompaniaRepository;
import com.gymadmin.platform.domain.port.out.ConfigNotifRepository;
import com.gymadmin.platform.domain.port.out.NotificacionRepository;
import com.gymadmin.platform.domain.port.out.PlanRepository;
import com.gymadmin.platform.domain.validation.PhoneNumberE164Normalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REQ-SAAS-001 (Sub-fase 1.5) + Fase 3 (WhatsApp): job diario que genera notificaciones de
 * vencimiento de la suscripción del dueño.
 *
 * <p>Corre ~10 minutos después del {@code SubscriptionJobService} para no pisarlo. Para cada
 * suscripción ACTIVA/EN_GRACIA con {@code fecha_fin} próxima, evalúa los buckets del dueño
 * <b>{3, 0}</b> (aviso previo a 3 días + día del vencimiento) y encola una notificación por canal.
 *
 * <p><b>Buckets {3, 0} (Fase 3):</b> el recordatorio del dueño es "3 días no más" — se reemplazó el
 * histórico {@code {15,7,3,1,0}}. El {@code 3} (previo) será configurable desde el panel super_admin
 * (Fase 6); el {@code 0} es fijo. <b>R2</b>: el día 0 se evalúa por igualdad (no cae al bucket previo).
 *
 * <p><b>Canal WhatsApp (Fase 3):</b> el canal a encolar sale de {@code config_notif_suscripcion.canal}
 * del tenant ({@code email}/{@code whatsapp}/{@code ambos}); el {@code banner} se mantiene siempre.
 * <b>R4</b>: solo se encola {@code whatsapp} si {@code compania.acepta_whatsapp = TRUE} y el teléfono
 * es normalizable a E.164; en otro caso se omite ese canal (sin error).
 */
@Component
public class NotificacionVencimientoJob {

    private static final Logger log = LoggerFactory.getLogger(NotificacionVencimientoJob.class);

    /** Buckets del dueño (Fase 3): aviso previo a 3 días + día del vencimiento. */
    private static final int BUCKET_PREVIO = 3;
    private static final int BUCKET_DIA_0 = 0;

    private static final String CODIGO_PLAN_TRIAL = "TRIAL";
    private static final String CODIGO_PLAN_PREMIUM = "PREMIUM";

    private final CompaniaPlanRepository companiaPlanRepository;
    private final PlanRepository planRepository;
    private final NotificacionRepository notificacionRepository;
    private final ConfigNotifRepository configNotifRepository;
    private final CompaniaRepository companiaRepository;
    private final EnviarNotificacionUseCase enviarUseCase;
    private final Clock clock;

    public NotificacionVencimientoJob(CompaniaPlanRepository companiaPlanRepository,
                                       PlanRepository planRepository,
                                       NotificacionRepository notificacionRepository,
                                       ConfigNotifRepository configNotifRepository,
                                       CompaniaRepository companiaRepository,
                                       EnviarNotificacionUseCase enviarUseCase,
                                       Clock clock) {
        this.companiaPlanRepository = companiaPlanRepository;
        this.planRepository = planRepository;
        this.notificacionRepository = notificacionRepository;
        this.configNotifRepository = configNotifRepository;
        this.companiaRepository = companiaRepository;
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
                .filter(cp -> ChronoUnit.DAYS.between(today, cp.getFechaFin()) <= (long) BUCKET_PREVIO)
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

        // R2: el día 0 usa el bucket 0 por igualdad; 1..3 usan el previo. Vencidos (<0) no se encolan.
        Integer bucket = resolverBucket(diasRestantes);
        if (bucket == null) {
            return Flux.empty();
        }
        return encolarSiNoExiste(cp, tipo, bucket);
    }

    /** R2: día 0 → bucket 0; 1..3 → bucket previo (3); cualquier otro (incluye <0) → null (no encolar). */
    private static Integer resolverBucket(long diasRestantes) {
        if (diasRestantes == 0) {
            return BUCKET_DIA_0;
        }
        if (diasRestantes >= 1 && diasRestantes <= BUCKET_PREVIO) {
            return BUCKET_PREVIO;
        }
        return null;
    }

    private Flux<Long> encolarSiNoExiste(CompaniaPlan cp, String tipo, int bucket) {
        return companiaRepository.findById(cp.getIdCompania())
                .flatMapMany(compania -> resolverCanales(cp.getIdCompania(), compania, bucket)
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
                                )))));
    }

    /**
     * Canales a encolar para el tenant: {@code banner} siempre; {@code email}/{@code whatsapp} según
     * {@code config_notif_suscripcion.canal}. Si no hay config activa, default = {@code email}
     * (comportamiento previo). El canal {@code whatsapp} solo se incluye si hay opt-in + teléfono válido (R4).
     */
    private Flux<String> resolverCanales(Long idCompania, Compania compania, int bucket) {
        return configNotifRepository.findByIdCompania(idCompania)
                .filter(cfg -> !Boolean.FALSE.equals(cfg.getActivo()))
                .collectList()
                .map(configs -> {
                    ConfigNotifSuscripcion.Canal canalConfig = elegirCanal(configs, bucket);
                    List<String> canales = new ArrayList<>();
                    canales.add(NotificacionSuscripcion.CANAL_BANNER);

                    boolean quiereEmail = canalConfig == ConfigNotifSuscripcion.Canal.EMAIL
                            || canalConfig == ConfigNotifSuscripcion.Canal.AMBOS;
                    boolean quiereWhatsapp = canalConfig == ConfigNotifSuscripcion.Canal.WHATSAPP
                            || canalConfig == ConfigNotifSuscripcion.Canal.AMBOS;

                    if (quiereEmail) {
                        canales.add(NotificacionSuscripcion.CANAL_EMAIL);
                    }
                    if (quiereWhatsapp && puedeRecibirWhatsapp(compania, idCompania)) {
                        canales.add(NotificacionSuscripcion.CANAL_WHATSAPP);
                    }
                    return canales;
                })
                .flatMapMany(Flux::fromIterable);
    }

    /**
     * Elige el canal configurado. Prefiere la config cuyo {@code dias_antes} coincida con el bucket;
     * si no hay coincidencia exacta, toma la primera config activa; si no hay ninguna, default EMAIL.
     */
    private static ConfigNotifSuscripcion.Canal elegirCanal(List<ConfigNotifSuscripcion> configs, int bucket) {
        if (configs == null || configs.isEmpty()) {
            return ConfigNotifSuscripcion.Canal.EMAIL;
        }
        Optional<ConfigNotifSuscripcion> porBucket = configs.stream()
                .filter(c -> c.getDiasAntes() != null && c.getDiasAntes() == bucket)
                .findFirst();
        ConfigNotifSuscripcion elegida = porBucket.orElse(configs.get(0));
        return elegida.getCanal() != null ? elegida.getCanal() : ConfigNotifSuscripcion.Canal.EMAIL;
    }

    /** R4: opt-in explícito + teléfono normalizable a E.164. */
    private boolean puedeRecibirWhatsapp(Compania compania, Long idCompania) {
        if (!compania.isAceptaWhatsapp()) {
            log.debug("Compania {} — canal whatsapp omitido: no_consentimiento", idCompania);
            return false;
        }
        String raw = compania.getWhatsapp() != null ? compania.getWhatsapp() : compania.getTelefono();
        if (PhoneNumberE164Normalizer.normalizar(raw).isEmpty()) {
            log.debug("Compania {} — canal whatsapp omitido: telefono_invalido", idCompania);
            return false;
        }
        return true;
    }

    private static String templateKeyPorBucket(int bucket) {
        return "vencimiento_" + bucket + "d";
    }
}
