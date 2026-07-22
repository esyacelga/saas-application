package com.gymadmin.platform.infrastructure.scheduler;

import com.gymadmin.platform.domain.port.in.ProcesarColaEmailsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * REQ-SAAS-001 (Sub-fase 1.5): worker periódico que procesa la cola de emails.
 * Corre cada {@code notificacion.email.queue.fixed-delay-ms} (default 30s).
 *
 * <p>Opt-out por env var: seteando {@code JOBS_PROCESSORS_ENABLED=false}
 * ({@code jobs.processors.enabled=false}) el bean no se crea y el job no corre
 * (útil en Cloud Run cuando el procesamiento lo dispara un endpoint interno).
 */
@Component
@ConditionalOnProperty(name = "jobs.processors.enabled", havingValue = "true", matchIfMissing = true)
public class EmailQueueProcessorJob {

    private static final Logger log = LoggerFactory.getLogger(EmailQueueProcessorJob.class);

    private final ProcesarColaEmailsUseCase procesarColaUseCase;
    private final int batchSize;

    public EmailQueueProcessorJob(ProcesarColaEmailsUseCase procesarColaUseCase,
                                   @Value("${notificacion.email.queue.batch-size:50}") int batchSize) {
        this.procesarColaUseCase = procesarColaUseCase;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${notificacion.email.queue.fixed-delay-ms:30000}")
    public void procesarLote() {
        procesarColaUseCase.procesarLote(batchSize)
                .subscribe(
                        cantidad -> {
                            if (cantidad > 0) {
                                log.debug("Cola de emails: procesados={}", cantidad);
                            }
                        },
                        err -> log.error("Fallo procesando cola de emails", err)
                );
    }
}
