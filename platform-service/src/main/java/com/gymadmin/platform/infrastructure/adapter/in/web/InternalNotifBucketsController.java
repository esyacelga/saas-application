package com.gymadmin.platform.infrastructure.adapter.in.web;

import com.gymadmin.platform.domain.model.NotifBucketGlobal;
import com.gymadmin.platform.domain.model.NotifBucketGlobal.Destinatario;
import com.gymadmin.platform.domain.port.out.NotifBucketGlobalRepository;
import com.gymadmin.platform.infrastructure.exception.ForbiddenException;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fase 6 (R1): endpoint interno para que <b>attendance-service</b> (job del socio) lea el bucket de
 * aviso previo global sin acceder al esquema {@code saas} (que es propiedad de platform-service).
 *
 * <p>Autenticación inter-service: header {@code X-Internal-Call} con el secreto compartido
 * {@code services.internal.secret} (mismo patrón que {@link InternalPlatformController}). Fuera del
 * filtro JWT — la ruta {@code /internal/**} es {@code permitAll()}.
 *
 * <p>Respuesta: {@code {destinatario, dias_previo, activo}}. Si el bucket está {@code activo=false},
 * el consumidor decide (attendance interpreta {@code activo=false} como "solo día 0").
 */
@Hidden
@RestController
@RequestMapping("/internal/v1")
public class InternalNotifBucketsController {

    /** Header del contrato interno service-to-service (no JWT). Mismo nombre en core/attendance. */
    public static final String HEADER_INTERNAL_CALL = "X-Internal-Call";

    private final NotifBucketGlobalRepository repository;
    private final String internalSecret;

    public InternalNotifBucketsController(
            NotifBucketGlobalRepository repository,
            @Value("${services.internal.secret:platform-secret-dev}") String internalSecret) {
        this.repository = repository;
        this.internalSecret = internalSecret;
    }

    @GetMapping("/notif-buckets/{destinatario}")
    public Mono<ResponseEntity<Map<String, Object>>> getBucket(
            @PathVariable("destinatario") String destinatario,
            @RequestHeader(value = HEADER_INTERNAL_CALL, required = false) String internalCall) {

        if (internalCall == null || !internalCall.equals(internalSecret)) {
            return Mono.error(new ForbiddenException("Invalid internal call"));
        }

        Destinatario dest;
        try {
            dest = Destinatario.fromCodigo(destinatario);
        } catch (IllegalArgumentException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("codigo", "destinatario_invalido");
            body.put("mensaje", "Destinatario desconocido: " + destinatario);
            return Mono.just(ResponseEntity.badRequest().body(body));
        }

        return repository.findByDestinatario(dest)
                .map(this::toBody)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    private Map<String, Object> toBody(NotifBucketGlobal b) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("destinatario", b.getDestinatario().getCodigo());
        body.put("dias_previo", b.getDiasPrevio());
        body.put("activo", b.isActivo());
        return body;
    }
}
