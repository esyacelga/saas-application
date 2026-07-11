package com.gymadmin.platform.application.service;

import com.gymadmin.platform.domain.exception.PagoYaProcesadoException;
import com.gymadmin.platform.domain.model.ActividadPlataforma.TipoActor;
import com.gymadmin.platform.domain.model.PagoPendienteValidacion;
import com.gymadmin.platform.domain.port.in.ActividadPlataformaUseCase;
import com.gymadmin.platform.domain.port.in.EnviarNotificacionUseCase;
import com.gymadmin.platform.domain.port.in.RechazarPagoUseCase;
import com.gymadmin.platform.domain.port.out.PagoPendienteValidacionRepository;
import com.gymadmin.platform.infrastructure.exception.BusinessException;
import com.gymadmin.platform.infrastructure.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * REQ-SAAS-001 (RN-08): rechazo manual de un pago pendiente por parte del
 * operador root. El motivo es obligatorio (≥ 10 chars). La transición
 * PENDIENTE→RECHAZADO se hace con UPDATE atómico (single row affected).
 */
@Service
public class RechazarPagoService implements RechazarPagoUseCase {

    private static final Logger log = LoggerFactory.getLogger(RechazarPagoService.class);
    private static final int MOTIVO_MIN_LEN = 10;

    private final PagoPendienteValidacionRepository pagoRepository;
    private final ActividadPlataformaUseCase actividadPlataformaUseCase;
    private final EnviarNotificacionUseCase enviarNotificacionUseCase;
    private final Clock clock;

    public RechazarPagoService(PagoPendienteValidacionRepository pagoRepository,
                                ActividadPlataformaUseCase actividadPlataformaUseCase,
                                EnviarNotificacionUseCase enviarNotificacionUseCase,
                                Clock clock) {
        this.pagoRepository = pagoRepository;
        this.actividadPlataformaUseCase = actividadPlataformaUseCase;
        this.enviarNotificacionUseCase = enviarNotificacionUseCase;
        this.clock = clock;
    }

    @Override
    public Mono<Void> rechazar(Long idPagoPendiente, Long idUsuarioRoot, String motivo) {
        if (motivo == null || motivo.trim().length() < MOTIVO_MIN_LEN) {
            return Mono.error(new BusinessException(
                    "El motivo de rechazo es obligatorio (mínimo " + MOTIVO_MIN_LEN + " caracteres)"));
        }
        Instant now = Instant.now(clock);
        return pagoRepository.marcarRechazado(idPagoPendiente, idUsuarioRoot, motivo.trim(), now)
                .flatMap(rows -> {
                    if (rows == null || rows == 0L) {
                        return Mono.<PagoPendienteValidacion>error(new PagoYaProcesadoException(
                                "El pago " + idPagoPendiente + " ya fue procesado por otro operador",
                                idPagoPendiente));
                    }
                    return pagoRepository.findById(idPagoPendiente)
                            .switchIfEmpty(Mono.error(new NotFoundException("PagoPendienteValidacion", idPagoPendiente)));
                })
                .flatMap(pago -> registrarEventoRechazo(pago, idUsuarioRoot, motivo.trim())
                        .then(encolarEmailPagoRechazado(pago.getIdCompania())))
                .then();
    }

    /**
     * REQ-SAAS-001 Sub-fase 1.6: encola el email {@code PAGO_RECHAZADO} al owner.
     * El renderer del email lee el motivo/fecha del propio pago (query separado).
     * El fallo del encolado no debe romper el rechazo (fire-and-forget con log).
     */
    private Mono<Void> encolarEmailPagoRechazado(Long idCompania) {
        return enviarNotificacionUseCase.encolar(new EnviarNotificacionUseCase.EncolarNotificacionCommand(
                        idCompania,
                        null,
                        "PAGO_RECHAZADO",
                        null,
                        "email",
                        "pago_rechazado",
                        null,
                        null))
                .doOnError(err -> log.warn("No se pudo encolar email PAGO_RECHAZADO (compania={}): {}",
                        idCompania, err.getMessage()))
                .onErrorResume(err -> Mono.empty())
                .then();
    }

    private Mono<Void> registrarEventoRechazo(PagoPendienteValidacion pago, Long idUsuarioRoot, String motivo) {
        Map<String, Object> detalle = new HashMap<>();
        detalle.put("id_pago_pendiente", pago.getId());
        detalle.put("id_plan_destino", pago.getIdPlanDestino());
        detalle.put("motivo", motivo);
        return actividadPlataformaUseCase.registrar(new ActividadPlataformaUseCase.RegistrarActividadCommand(
                "PAGO_RECHAZADO",
                TipoActor.ROOT,
                idUsuarioRoot,
                null,
                pago.getIdCompania(),
                detalle
        ));
    }
}
