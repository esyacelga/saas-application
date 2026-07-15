package com.gymadmin.platform.infrastructure.scheduler;

import com.gymadmin.platform.domain.port.in.ProcesarColaWhatsAppUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * REQ-SAAS-001 (Fase 3): worker periódico que procesa la cola de WhatsApp del dueño.
 * Hermano de {@link EmailQueueProcessorJob}: mismo patrón, cola distinta ({@code canal='whatsapp'}).
 * Corre cada {@code notificacion.whatsapp.queue.fixed-delay-ms} (default 30s).
 */
@Component
public class WhatsAppQueueProcessorJob {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppQueueProcessorJob.class);

    private final ProcesarColaWhatsAppUseCase procesarColaUseCase;
    private final int batchSize;

    public WhatsAppQueueProcessorJob(ProcesarColaWhatsAppUseCase procesarColaUseCase,
                                      @Value("${notificacion.whatsapp.queue.batch-size:50}") int batchSize) {
        this.procesarColaUseCase = procesarColaUseCase;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${notificacion.whatsapp.queue.fixed-delay-ms:30000}")
    public void procesarLote() {
        procesarColaUseCase.procesarLote(batchSize)
                .subscribe(
                        cantidad -> {
                            if (cantidad > 0) {
                                log.debug("Cola de WhatsApp: procesados={}", cantidad);
                            }
                        },
                        err -> log.error("Fallo procesando cola de WhatsApp", err)
                );
    }
}
