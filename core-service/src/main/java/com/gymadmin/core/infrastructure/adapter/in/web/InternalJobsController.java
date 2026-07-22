package com.gymadmin.core.infrastructure.adapter.in.web;

import com.gymadmin.core.application.service.ClienteStatusJobService;
import com.gymadmin.core.infrastructure.adapter.out.http.PlatformServiceClient;
import com.gymadmin.core.infrastructure.exception.ForbiddenException;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * REQ-SAAS-001 (Cloud Run deploy): endpoint interno para disparar manualmente los jobs
 * programados del servicio, dado que Cloud Run no ejecuta {@code @Scheduled} (el pod puede
 * escalar a 0). Autenticación por header {@code X-Internal-Call} con secreto compartido.
 * <p>
 * En Cloud Run se desactiva el cron nativo seteando la env var
 * {@code CLIENT_STATUS_JOB_CRON=-} y se invoca este endpoint desde Cloud Scheduler.
 */
@Hidden
@RestController
@RequestMapping("/internal/v1")
public class InternalJobsController {

    private final ClienteStatusJobService clienteStatusJobService;
    private final String internalSecret;

    public InternalJobsController(ClienteStatusJobService clienteStatusJobService,
                                  @Value("${services.internal.secret:${INTERNAL_SECRET:platform-secret-dev}}") String internalSecret) {
        this.clienteStatusJobService = clienteStatusJobService;
        this.internalSecret = internalSecret;
    }

    @PostMapping("/jobs/run-generators")
    public Mono<ResponseEntity<GeneratorsResponse>> runGenerators(
            @RequestHeader(value = PlatformServiceClient.HEADER_INTERNAL_CALL, required = false) String internalCall) {

        if (!secretoValido(internalCall)) {
            return Mono.error(new ForbiddenException("Invalid internal call"));
        }

        return Mono.fromRunnable(clienteStatusJobService::ejecutar)
                .thenReturn(ResponseEntity.ok(
                        new GeneratorsResponse(new ClienteStatusResponse("ok"))));
    }

    private boolean secretoValido(String internalCall) {
        return internalCall != null && internalCall.equals(internalSecret);
    }

    public record ClienteStatusResponse(String status) {}

    public record GeneratorsResponse(ClienteStatusResponse clienteStatus) {}
}
