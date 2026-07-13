package com.gymadmin.platform.application.service;

import com.gymadmin.platform.domain.exception.PagoYaProcesadoException;
import com.gymadmin.platform.domain.model.ActividadPlataforma.TipoActor;
import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.model.PagoPendienteValidacion;
import com.gymadmin.platform.domain.port.in.ActividadPlataformaUseCase;
import com.gymadmin.platform.domain.port.in.AprobarPagoUseCase;
import com.gymadmin.platform.domain.port.in.ModuloCheckUseCase;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.domain.port.out.PagoPendienteValidacionRepository;
import com.gymadmin.platform.domain.port.out.PlanRepository;
import com.gymadmin.platform.infrastructure.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * REQ-SAAS-001 (RN-08): aprobación manual de un pago pendiente por parte del
 * operador root. La transición PENDIENTE→APROBADO se hace con UPDATE atómico
 * (single row affected). Si el pago tiene {@code activacionProgramada=true}
 * (owner en Trial), la nueva suscripción Premium queda PROGRAMADA para el día
 * siguiente al fin del Trial; en caso contrario, se activa inmediatamente.
 */
@Service
public class AprobarPagoService implements AprobarPagoUseCase {

    private static final Logger log = LoggerFactory.getLogger(AprobarPagoService.class);
    private static final int DURACION_PREMIUM_DEFAULT_DIAS = 30;

    private final PagoPendienteValidacionRepository pagoRepository;
    private final CompaniaPlanRepository companiaPlanRepository;
    private final PlanRepository planRepository;
    private final ActividadPlataformaUseCase actividadPlataformaUseCase;
    private final ModuloCheckUseCase moduloCheckUseCase;
    private final Clock clock;

    public AprobarPagoService(PagoPendienteValidacionRepository pagoRepository,
                               CompaniaPlanRepository companiaPlanRepository,
                               PlanRepository planRepository,
                               ActividadPlataformaUseCase actividadPlataformaUseCase,
                               ModuloCheckUseCase moduloCheckUseCase,
                               Clock clock) {
        this.pagoRepository = pagoRepository;
        this.companiaPlanRepository = companiaPlanRepository;
        this.planRepository = planRepository;
        this.actividadPlataformaUseCase = actividadPlataformaUseCase;
        this.moduloCheckUseCase = moduloCheckUseCase;
        this.clock = clock;
    }

    @Override
    public Mono<CompaniaPlan> aprobar(Long idPagoPendiente, Long idUsuarioRoot) {
        Instant now = Instant.now(clock);
        return pagoRepository.marcarAprobado(idPagoPendiente, idUsuarioRoot, now)
                .flatMap(rows -> {
                    if (rows == null || rows == 0L) {
                        return Mono.error(new PagoYaProcesadoException(
                                "El pago " + idPagoPendiente + " ya fue procesado por otro operador",
                                idPagoPendiente));
                    }
                    return pagoRepository.findById(idPagoPendiente)
                            .switchIfEmpty(Mono.error(new NotFoundException("PagoPendienteValidacion", idPagoPendiente)));
                })
                .flatMap(pago -> crearSuscripcionPremium(pago)
                        .flatMap(nuevaSuscripcion -> registrarEventoAprobacion(pago, idUsuarioRoot, nuevaSuscripcion.getId())
                                .then(moduloCheckUseCase.invalidateCacheByCompania(pago.getIdCompania())
                                        .onErrorResume(err -> {
                                            log.warn("No se pudo invalidar cache tras aprobar pago: {}", err.getMessage());
                                            return Mono.just(0L);
                                        }))
                                .thenReturn(nuevaSuscripcion)));
    }

    private Mono<CompaniaPlan> crearSuscripcionPremium(PagoPendienteValidacion pago) {
        return planRepository.findById(pago.getIdPlanDestino())
                .switchIfEmpty(Mono.error(new NotFoundException("Plan", pago.getIdPlanDestino())))
                .flatMap(planPremium -> resolverFechaInicio(pago)
                        .flatMap(fechaInicio -> {
                            CompaniaPlan nueva = new CompaniaPlan();
                            nueva.setIdCompania(pago.getIdCompania());
                            nueva.setIdPlan(planPremium.getId());
                            nueva.setFechaInicio(fechaInicio);
                            int duracion = planPremium.getDuracionDias() != null
                                    ? planPremium.getDuracionDias()
                                    : DURACION_PREMIUM_DEFAULT_DIAS;
                            nueva.setFechaFin(fechaInicio.plusDays(duracion));
                            nueva.setDiasGracia(0);
                            nueva.setEstado(pago.isActivacionProgramada()
                                    ? CompaniaPlan.Estado.PROGRAMADO
                                    : CompaniaPlan.Estado.ACTIVO);
                            nueva.setTipoCambio(pago.isActivacionProgramada()
                                    ? CompaniaPlan.TipoCambio.UPGRADE
                                    : CompaniaPlan.TipoCambio.NUEVO);
                            // Activación inmediata: reemplazar la suscripción vigente para no
                            // violar ux_compania_plan_vigente (único parcial sobre ACTIVO/EN_GRACIA).
                            // En activación programada no se toca: el SubscriptionJobService lo hará
                            // al llegar la fecha_inicio.
                            if (pago.isActivacionProgramada()) {
                                return companiaPlanRepository.save(nueva);
                            }
                            return reemplazarActivoPrevio(pago.getIdCompania())
                                    .then(companiaPlanRepository.save(nueva));
                        }));
    }

    private Mono<Void> reemplazarActivoPrevio(Long idCompania) {
        return companiaPlanRepository.findActivoByIdCompania(idCompania)
                .flatMap(previo -> {
                    previo.setEstado(CompaniaPlan.Estado.REEMPLAZADA);
                    return companiaPlanRepository.save(previo)
                            .doOnSuccess(s -> log.debug("Plan {} pasó a REEMPLAZADA por aprobación de pago", s.getId()));
                })
                .then();
    }

    private Mono<LocalDate> resolverFechaInicio(PagoPendienteValidacion pago) {
        LocalDate hoy = LocalDate.now(clock);
        if (!pago.isActivacionProgramada()) {
            return Mono.just(hoy);
        }
        // RN-05: si es Trial→Premium programado, la fecha_inicio es el fecha_fin del Trial activo actual.
        return companiaPlanRepository.findActivoByIdCompania(pago.getIdCompania())
                .map(trial -> trial.getFechaFin().plusDays(1))
                .defaultIfEmpty(hoy);
    }

    private Mono<Void> registrarEventoAprobacion(PagoPendienteValidacion pago, Long idUsuarioRoot, Long idNuevaSuscripcion) {
        Map<String, Object> detalle = new HashMap<>();
        detalle.put("id_pago_pendiente", pago.getId());
        detalle.put("id_plan_destino", pago.getIdPlanDestino());
        detalle.put("id_compania_plan_nuevo", idNuevaSuscripcion);
        detalle.put("monto", pago.getMonto() != null ? pago.getMonto().toPlainString() : "");
        detalle.put("activacion_programada", pago.isActivacionProgramada());
        return actividadPlataformaUseCase.registrar(new ActividadPlataformaUseCase.RegistrarActividadCommand(
                "PAGO_APROBADO",
                TipoActor.ROOT,
                idUsuarioRoot,
                null,
                pago.getIdCompania(),
                detalle
        ));
    }
}
