package com.gymadmin.platform.infrastructure.adapter.in.web;

import com.gymadmin.platform.application.service.SubscriptionJobService;
import com.gymadmin.platform.domain.port.in.ProcesarColaEmailsUseCase;
import com.gymadmin.platform.domain.port.in.ProcesarColaWhatsAppUseCase;
import com.gymadmin.platform.infrastructure.exception.ForbiddenException;
import com.gymadmin.platform.infrastructure.scheduler.NotificacionVencimientoJob;
import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoint interno para disparar manualmente los jobs programados del servicio
 * (útil para debug desde Postman/PowerShell en local, staging y Cloud Run cuando
 * los cron están desactivados con {@code "-"}).
 *
 * <p>Autenticación inter-service: header {@code X-Internal-Call} con el secreto
 * compartido {@code services.internal.secret} (mismo patrón que
 * {@link InternalPlatformController} y {@link InternalNotifBucketsController}).
 * Fuera del filtro JWT — la ruta {@code /internal/**} es {@code permitAll()} en
 * {@code SecurityConfig} y la validación del header se hace inline.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST /internal/v1/jobs/run-generators} — dispara SubscriptionJob + NotificacionVencimientoJob.</li>
 *   <li>{@code POST /internal/v1/jobs/run-processors} — dispara procesamiento de colas Email + WhatsApp.</li>
 *   <li>{@code POST /internal/v1/jobs/run-all} — combina generators + processors en secuencia.</li>
 * </ul>
 */
@Hidden
@RestController
@RequestMapping("/internal/v1")
public class InternalJobsController {

    /** Header del contrato interno service-to-service (no JWT). */
    public static final String HEADER_INTERNAL_CALL = "X-Internal-Call";

    private static final Logger log = LoggerFactory.getLogger(InternalJobsController.class);

    private final SubscriptionJobService subscriptionJobService;
    private final NotificacionVencimientoJob notificacionVencimientoJob;
    private final ProcesarColaEmailsUseCase procesarColaEmailsUseCase;
    private final ProcesarColaWhatsAppUseCase procesarColaWhatsAppUseCase;
    private final int emailBatchSize;
    private final int whatsappBatchSize;
    private final String internalSecret;

    public InternalJobsController(
            SubscriptionJobService subscriptionJobService,
            NotificacionVencimientoJob notificacionVencimientoJob,
            ProcesarColaEmailsUseCase procesarColaEmailsUseCase,
            ProcesarColaWhatsAppUseCase procesarColaWhatsAppUseCase,
            @Value("${notificacion.email.queue.batch-size:50}") int emailBatchSize,
            @Value("${notificacion.whatsapp.queue.batch-size:50}") int whatsappBatchSize,
            @Value("${services.internal.secret:platform-secret-dev}") String internalSecret) {
        this.subscriptionJobService = subscriptionJobService;
        this.notificacionVencimientoJob = notificacionVencimientoJob;
        this.procesarColaEmailsUseCase = procesarColaEmailsUseCase;
        this.procesarColaWhatsAppUseCase = procesarColaWhatsAppUseCase;
        this.emailBatchSize = emailBatchSize;
        this.whatsappBatchSize = whatsappBatchSize;
        this.internalSecret = internalSecret;
    }

    // ── DTOs ──────────────────────────────────────────────────────────────

    /** Resumen del subscription job. Sin contadores por ahora (retorna void y hace subscribe interno). */
    public record GeneratorStatus(String status) {}

    /** Resumen del processor: {@code procesados} = filas devueltas por procesarLote(batchSize). */
    public record ProcessorSummary(int procesados) {}

    public record GeneratorsResponse(GeneratorStatus subscriptions,
                                      GeneratorStatus notificacionVencimiento) {}

    public record ProcessorsResponse(ProcessorSummary email, ProcessorSummary whatsapp) {}

    public record RunAllResponse(GeneratorsResponse generators, ProcessorsResponse processors) {}

    // ── Endpoints ─────────────────────────────────────────────────────────

    @PostMapping("/jobs/run-generators")
    public Mono<ResponseEntity<GeneratorsResponse>> runGenerators(
            @RequestHeader(value = HEADER_INTERNAL_CALL, required = false) String internalCall) {
        return validarHeader(internalCall)
                .then(disparaGenerators())
                .map(ResponseEntity::ok);
    }

    @PostMapping("/jobs/run-processors")
    public Mono<ResponseEntity<ProcessorsResponse>> runProcessors(
            @RequestHeader(value = HEADER_INTERNAL_CALL, required = false) String internalCall) {
        return validarHeader(internalCall)
                .then(disparaProcessors())
                .map(ResponseEntity::ok);
    }

    @PostMapping("/jobs/run-all")
    public Mono<ResponseEntity<RunAllResponse>> runAll(
            @RequestHeader(value = HEADER_INTERNAL_CALL, required = false) String internalCall) {
        return validarHeader(internalCall)
                .then(disparaGenerators())
                .flatMap(gen -> disparaProcessors()
                        .map(proc -> new RunAllResponse(gen, proc)))
                .map(ResponseEntity::ok);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private Mono<Void> validarHeader(String internalCall) {
        if (internalCall == null || !internalCall.equals(internalSecret)) {
            return Mono.error(new ForbiddenException("Invalid internal call"));
        }
        return Mono.empty();
    }

    /**
     * Dispara los 2 generators.
     *
     * <p>TODO: exponer contadores reales cuando los services publiquen métricas
     * (hoy {@code runSubscriptionJob()} y {@code ejecutar()} son {@code void}
     * y hacen {@code .subscribe(...)} internamente — fire-and-forget).
     */
    private Mono<GeneratorsResponse> disparaGenerators() {
        return Mono.fromRunnable(() -> {
                    log.info("[InternalJobs] Disparando SubscriptionJob manualmente");
                    subscriptionJobService.runSubscriptionJob();
                    log.info("[InternalJobs] Disparando NotificacionVencimientoJob manualmente");
                    notificacionVencimientoJob.ejecutar();
                })
                .thenReturn(new GeneratorsResponse(
                        new GeneratorStatus("ok"),
                        new GeneratorStatus("ok")
                ));
    }

    /**
     * Dispara los 2 processors en secuencia y devuelve la cantidad procesada por cada uno.
     * A diferencia de los generators, {@code procesarLote(batchSize)} sí devuelve {@code Mono<Integer>}.
     */
    private Mono<ProcessorsResponse> disparaProcessors() {
        return procesarColaEmailsUseCase.procesarLote(emailBatchSize)
                .doOnNext(cantidad -> log.info("[InternalJobs] Cola email procesados={}", cantidad))
                .flatMap(email -> procesarColaWhatsAppUseCase.procesarLote(whatsappBatchSize)
                        .doOnNext(cantidad -> log.info("[InternalJobs] Cola whatsapp procesados={}", cantidad))
                        .map(whatsapp -> new ProcessorsResponse(
                                new ProcessorSummary(email),
                                new ProcessorSummary(whatsapp)
                        )));
    }

    // Nota estilo: se sigue el patrón de LinkedHashMap de los otros controllers internos
    // solo si el shape es dinámico. Aquí el shape es fijo y tipado con records — más claro.
    @SuppressWarnings("unused")
    private static Map<String, Object> ok() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ok");
        return body;
    }
}
