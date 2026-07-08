package com.gymadmin.platform.application.service;

import com.gymadmin.platform.domain.model.CompaniaPlan;
import com.gymadmin.platform.domain.model.ModuloCheckResult;
import com.gymadmin.platform.domain.port.in.ModuloCheckUseCase;
import com.gymadmin.platform.domain.port.out.CompaniaPlanRepository;
import com.gymadmin.platform.domain.port.out.ModuloCheckCache;
import com.gymadmin.platform.domain.port.out.PlanRepository;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class ModuloCheckService implements ModuloCheckUseCase {

    private final CompaniaPlanRepository companiaPlanRepository;
    private final PlanRepository planRepository;
    private final ModuloCheckCache cache;
    private final DatabaseClient databaseClient;

    public ModuloCheckService(CompaniaPlanRepository companiaPlanRepository,
                               PlanRepository planRepository,
                               ModuloCheckCache cache,
                               DatabaseClient databaseClient) {
        this.companiaPlanRepository = companiaPlanRepository;
        this.planRepository = planRepository;
        this.cache = cache;
        this.databaseClient = databaseClient;
    }

    @Override
    public Mono<ModuloCheckResult> checkAcceso(Long idCompania, String codigo) {
        return cache.get(idCompania, codigo)
                .switchIfEmpty(Mono.defer(() -> computeCheckAcceso(idCompania, codigo)
                        .flatMap(result -> cache.put(idCompania, codigo, result, Duration.ofSeconds(300))
                                .thenReturn(result))
                ));
    }

    private Mono<ModuloCheckResult> computeCheckAcceso(Long idCompania, String codigo) {
        return companiaPlanRepository.findActivoByIdCompania(idCompania)
                .flatMap(cp -> checkCaracteristicaInPlan(cp.getIdPlan(), codigo)
                        .flatMap(hasAccess -> {
                            if (!hasAccess) {
                                return Mono.just(ModuloCheckResult.denied("modulo_no_incluido"));
                            }
                            return planRepository.findById(cp.getIdPlan())
                                    .map(plan -> ModuloCheckResult.allowed(plan.getNombre()))
                                    .defaultIfEmpty(ModuloCheckResult.allowed("Unknown"));
                        }))
                .switchIfEmpty(Mono.defer(() ->
                        companiaPlanRepository.findHistorialByIdCompania(idCompania)
                                .next()
                                .map(latest -> latest.getEstado() == CompaniaPlan.Estado.SUSPENDIDO
                                        ? ModuloCheckResult.denied("plan_suspendido")
                                        : ModuloCheckResult.denied("plan_vencido"))
                                .defaultIfEmpty(ModuloCheckResult.denied("plan_vencido"))
                ));
    }

    private Mono<Boolean> checkCaracteristicaInPlan(Long idPlan, String codigo) {
        return databaseClient.sql(
                "SELECT COUNT(*) as cnt FROM saas.plan_caracteristicas pc " +
                "JOIN saas.caracteristicas c ON pc.id_caracteristica = c.id " +
                "WHERE pc.id_plan = :idPlan AND c.codigo = :codigo AND c.activo = true")
                .bind("idPlan", idPlan)
                .bind("codigo", codigo)
                .map((row, metadata) -> {
                    Number count = row.get("cnt", Number.class);
                    return count != null && count.longValue() > 0;
                })
                .one()
                .defaultIfEmpty(false);
    }
}
