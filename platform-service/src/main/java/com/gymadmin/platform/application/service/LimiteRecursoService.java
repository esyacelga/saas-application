package com.gymadmin.platform.application.service;

import com.gymadmin.platform.domain.exception.LimiteAlcanzadoException;
import com.gymadmin.platform.domain.model.ActividadPlataforma.TipoActor;
import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.model.Plan;
import com.gymadmin.platform.domain.model.RecursoLimitable;
import com.gymadmin.platform.domain.port.in.ActividadPlataformaUseCase;
import com.gymadmin.platform.domain.port.in.LimiteRecursoUseCase;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.domain.port.out.PlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * REQ-SAAS-001 (RN-05): valida cuotas duras de un plan antes de crear un recurso.
 * <p>
 * Uso concurrente serializado con {@code pg_advisory_xact_lock(idCompania)} sobre
 * la transacción R2DBC vigente — dos peticiones simultáneas del mismo tenant
 * quedan encoladas hasta que la primera termine.
 * <p>
 * TODOs Sub-fase 1.4:
 * <ul>
 *   <li>{@link #contarClientesActivos(Long)} — HTTP call a core-service.</li>
 *   <li>{@link #contarStaff(Long)} — HTTP call a auth-service.</li>
 * </ul>
 */
@Service
public class LimiteRecursoService implements LimiteRecursoUseCase {

    private static final Logger log = LoggerFactory.getLogger(LimiteRecursoService.class);

    private final DatabaseClient databaseClient;
    private final CompaniaPlanRepository companiaPlanRepository;
    private final PlanRepository planRepository;
    private final ActividadPlataformaUseCase actividadPlataformaUseCase;

    public LimiteRecursoService(DatabaseClient databaseClient,
                                 CompaniaPlanRepository companiaPlanRepository,
                                 PlanRepository planRepository,
                                 ActividadPlataformaUseCase actividadPlataformaUseCase) {
        this.databaseClient = databaseClient;
        this.companiaPlanRepository = companiaPlanRepository;
        this.planRepository = planRepository;
        this.actividadPlataformaUseCase = actividadPlataformaUseCase;
    }

    @Override
    @Transactional
    public Mono<Void> validarPuedeCrear(Long idCompania, RecursoLimitable recurso) {
        return adquirirAdvisoryLock(idCompania)
                .then(companiaPlanRepository.findActivoByIdCompania(idCompania))
                .flatMap(cp -> planRepository.findById(cp.getIdPlan())
                        .flatMap(plan -> validarContraPlan(idCompania, recurso, cp, plan)))
                // Sin suscripción activa: no bloqueamos aquí — el caller decide.
                .switchIfEmpty(Mono.empty())
                .then();
    }

    private Mono<Void> adquirirAdvisoryLock(Long idCompania) {
        return databaseClient.sql("SELECT pg_advisory_xact_lock(:idCompania)")
                .bind("idCompania", idCompania)
                .fetch()
                .rowsUpdated()
                .then();
    }

    private Mono<Void> validarContraPlan(Long idCompania,
                                          RecursoLimitable recurso,
                                          CompaniaPlan companiaPlan,
                                          Plan plan) {
        Integer maximo = maximoParaRecurso(plan, recurso);
        if (maximo == null) {
            return Mono.empty();
        }
        return contarUsoActual(idCompania, recurso)
                .flatMap(actual -> {
                    if (actual < maximo) {
                        return Mono.empty();
                    }
                    return registrarEventoLimite(idCompania, recurso, plan, actual, maximo)
                            .then(Mono.error(new LimiteAlcanzadoException(
                                    recurso, actual, maximo, plan.getCodigo())));
                });
    }

    private Integer maximoParaRecurso(Plan plan, RecursoLimitable recurso) {
        return switch (recurso) {
            case SUCURSALES -> plan.getMaxSucursales();
            case CLIENTES_ACTIVOS -> plan.getMaxClientesActivos();
            case STAFF -> plan.getMaxStaff();
        };
    }

    private Mono<Long> contarUsoActual(Long idCompania, RecursoLimitable recurso) {
        return switch (recurso) {
            case SUCURSALES -> contarSucursales(idCompania);
            case CLIENTES_ACTIVOS -> contarClientesActivos(idCompania);
            case STAFF -> contarStaff(idCompania);
        };
    }

    private Mono<Long> contarSucursales(Long idCompania) {
        return databaseClient.sql(
                "SELECT COUNT(*) AS cnt FROM tenant.sucursales " +
                "WHERE id_compania = :id AND eliminado = FALSE")
                .bind("id", idCompania)
                .map((row, meta) -> {
                    Number n = row.get("cnt", Number.class);
                    return n == null ? 0L : n.longValue();
                })
                .one()
                .defaultIfEmpty(0L);
    }

    /**
     * TODO Sub-fase 1.4 wiring cross-service: llamar a core-service
     * (ej. GET /internal/companias/{id}/clientes-activos/count) para obtener el
     * conteo real. Por ahora devuelve 0 para no bloquear la iteración.
     */
    private Mono<Long> contarClientesActivos(Long idCompania) {
        log.debug("contarClientesActivos({}) — stub: retorna 0 (Sub-fase 1.4 wiring cross-service)", idCompania);
        return Mono.just(0L);
    }

    /**
     * TODO Sub-fase 1.4 wiring cross-service: llamar a auth-service
     * (ej. GET /internal/companias/{id}/staff/count) para obtener el conteo real.
     * Por ahora devuelve 0 para no bloquear la iteración.
     */
    private Mono<Long> contarStaff(Long idCompania) {
        log.debug("contarStaff({}) — stub: retorna 0 (Sub-fase 1.4 wiring cross-service)", idCompania);
        return Mono.just(0L);
    }

    private Mono<Void> registrarEventoLimite(Long idCompania,
                                              RecursoLimitable recurso,
                                              Plan plan,
                                              long actual,
                                              long maximo) {
        return actividadPlataformaUseCase.registrar(new ActividadPlataformaUseCase.RegistrarActividadCommand(
                "LIMITE_FREE_ALCANZADO",
                TipoActor.SISTEMA,
                null,
                null,
                idCompania,
                Map.of(
                        "recurso", recurso.name(),
                        "actual", actual,
                        "maximo", maximo,
                        "plan_codigo", plan.getCodigo() != null ? plan.getCodigo() : ""
                )
        ));
    }
}
