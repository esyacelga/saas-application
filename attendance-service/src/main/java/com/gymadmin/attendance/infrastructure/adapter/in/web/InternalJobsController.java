package com.gymadmin.attendance.infrastructure.adapter.in.web;

import com.gymadmin.attendance.infrastructure.adapter.out.core.CoreServiceClient;
import com.gymadmin.attendance.infrastructure.exception.ForbiddenException;
import com.gymadmin.attendance.infrastructure.scheduler.MensajeriaJob;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Endpoint interno para disparar manualmente el {@link MensajeriaJob} en entornos donde no se puede
 * usar {@code @Scheduled} (por ejemplo, Cloud Run sin instancia siempre activa). Autenticación por
 * header {@code X-Internal-Call} con secreto compartido (no JWT), mismo patrón que
 * {@code InternalCoreController} de core-service e {@code InternalPlatformController} de platform.
 *
 * <p>La ruta {@code /internal/**} está en {@code permitAll()} en {@code SecurityConfig} para que
 * el filtro JWT no rechace la llamada antes de llegar al controller.
 */
@Hidden
@RestController
@RequestMapping("/internal/v1")
public class InternalJobsController {

    private final MensajeriaJob mensajeriaJob;
    private final String internalSecret;

    public InternalJobsController(MensajeriaJob mensajeriaJob,
                                  @Value("${services.core-service.internal-secret:${INTERNAL_SECRET:platform-secret-dev}}") String internalSecret) {
        this.mensajeriaJob = mensajeriaJob;
        this.internalSecret = internalSecret;
    }

    /**
     * Dispara el {@link MensajeriaJob#procesarAusencias()} (aviso previo de vencimiento por WhatsApp).
     * Espera a que el {@code Flux<Void>} complete antes de responder 200. El método no expone
     * contadores (todo el logging está dentro del job), así que la respuesta es fija
     * {@code { mensajeria: { status: "ok" } }}.
     */
    @PostMapping("/jobs/run-all")
    public Mono<ResponseEntity<RunAllResponse>> runAll(
            @RequestHeader(value = CoreServiceClient.HEADER_INTERNAL_CALL, required = false) String internalCall) {

        validarSecret(internalCall);

        return mensajeriaJob.procesarAusencias()
                .then()
                .thenReturn(ResponseEntity.ok(new RunAllResponse(new MensajeriaResponse("ok"))));
    }

    private void validarSecret(String header) {
        if (header == null || !header.equals(internalSecret)) {
            throw new ForbiddenException("Invalid internal call");
        }
    }

    public record MensajeriaResponse(String status) {}

    public record RunAllResponse(MensajeriaResponse mensajeria) {}
}
