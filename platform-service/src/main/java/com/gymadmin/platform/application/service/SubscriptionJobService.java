package com.gymadmin.platform.application.service;

import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.model.ConfigNotifSuscripcion;
import com.gymadmin.platform.domain.model.NotificacionSuscripcion;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.domain.port.out.ConfigNotifRepository;
import com.gymadmin.platform.domain.port.out.NotificacionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class SubscriptionJobService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionJobService.class);

    private final CompaniaPlanRepository companiaPlanRepository;
    private final ConfigNotifRepository configNotifRepository;
    private final NotificacionRepository notificacionRepository;

    public SubscriptionJobService(CompaniaPlanRepository companiaPlanRepository,
                                   ConfigNotifRepository configNotifRepository,
                                   NotificacionRepository notificacionRepository) {
        this.companiaPlanRepository = companiaPlanRepository;
        this.configNotifRepository = configNotifRepository;
        this.notificacionRepository = notificacionRepository;
    }

    @Scheduled(cron = "${subscription.job.cron:0 5 0 * * *}")
    public void runSubscriptionJob() {
        log.info("Starting subscription job at {}", LocalDateTime.now());
        LocalDate today = LocalDate.now();

        transitionActivosToEnGracia(today)
                .then(transitionEnGraciaToVencido(today))
                .then(activateProgramados(today))
                .then(processNotifications(today))
                .subscribe(
                        null,
                        error -> log.error("Subscription job failed", error),
                        () -> log.info("Subscription job completed successfully")
                );
    }

    private Mono<Void> transitionActivosToEnGracia(LocalDate today) {
        return companiaPlanRepository.findActivosVencidos(today)
                .flatMap(cp -> {
                    CompaniaPlan enGracia = createTransition(cp, CompaniaPlan.Estado.EN_GRACIA);
                    return companiaPlanRepository.save(enGracia)
                            .doOnSuccess(saved -> log.debug("Transitioned plan {} to EN_GRACIA", cp.getId()));
                })
                .then();
    }

    private Mono<Void> transitionEnGraciaToVencido(LocalDate today) {
        return companiaPlanRepository.findEnGraciaVencidos(today)
                .flatMap(cp -> {
                    CompaniaPlan vencido = createTransition(cp, CompaniaPlan.Estado.VENCIDO);
                    return companiaPlanRepository.save(vencido)
                            .doOnSuccess(saved -> log.debug("Transitioned plan {} to VENCIDO", cp.getId()));
                })
                .then();
    }

    private Mono<Void> activateProgramados(LocalDate today) {
        return companiaPlanRepository.findProgramadosParaActivar(today)
                .flatMap(cp -> {
                    CompaniaPlan activo = createTransition(cp, CompaniaPlan.Estado.ACTIVO);
                    return companiaPlanRepository.save(activo)
                            .doOnSuccess(saved -> log.debug("Activated programado plan {} to ACTIVO", cp.getId()));
                })
                .then();
    }

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
                                        notif.setFechaEnvio(LocalDateTime.now());
                                        return notificacionRepository.save(notif);
                                    })
                            );
                })
                .then();
    }

    private CompaniaPlan createTransition(CompaniaPlan source, CompaniaPlan.Estado newEstado) {
        CompaniaPlan transition = new CompaniaPlan();
        transition.setIdCompania(source.getIdCompania());
        transition.setIdPlan(source.getIdPlan());
        transition.setFechaInicio(source.getFechaInicio());
        transition.setFechaFin(source.getFechaFin());
        transition.setDiasGracia(source.getDiasGracia());
        transition.setFechaUltimoPago(source.getFechaUltimoPago());
        transition.setEstado(newEstado);
        transition.setTipoCambio(source.getTipoCambio());
        transition.setIdCompaniaPlanOrig(source.getId());
        transition.setCreditoMonto(source.getCreditoMonto());
        return transition;
    }
}
