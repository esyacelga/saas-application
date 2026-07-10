package com.gymadmin.platform.infrastructure.adapter.out.persistence.adapter;

import com.gymadmin.platform.domain.model.Caracteristica;
import com.gymadmin.platform.domain.model.Plan;
import com.gymadmin.platform.domain.port.out.PlanRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.CaracteristicaEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.entity.PlanEntity;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.CaracteristicaR2dbcRepository;
import com.gymadmin.platform.infrastructure.adapter.out.persistence.repository.PlanR2dbcRepository;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Component
public class PlanPersistenceAdapter implements PlanRepository {

    private final PlanR2dbcRepository planR2dbcRepository;
    private final CaracteristicaR2dbcRepository caracteristicaR2dbcRepository;
    private final DatabaseClient databaseClient;

    public PlanPersistenceAdapter(PlanR2dbcRepository planR2dbcRepository,
                                   CaracteristicaR2dbcRepository caracteristicaR2dbcRepository,
                                   DatabaseClient databaseClient) {
        this.planR2dbcRepository = planR2dbcRepository;
        this.caracteristicaR2dbcRepository = caracteristicaR2dbcRepository;
        this.databaseClient = databaseClient;
    }

    @Override
    public Flux<Plan> findAll() {
        return planR2dbcRepository.findAll()
                .map(this::toDomain);
    }

    @Override
    public Mono<Plan> findById(Long id) {
        return planR2dbcRepository.findById(id)
                .map(this::toDomain);
    }

    @Override
    public Mono<Plan> findByCodigo(String codigo) {
        return planR2dbcRepository.findByCodigo(codigo)
                .map(this::toDomain);
    }

    @Override
    public Flux<Plan> findByActivoTrueAndEsLegacyFalse() {
        return planR2dbcRepository.findByActivoTrueAndEsLegacyFalse()
                .flatMap(planEntity ->
                        loadCaracteristicas(planEntity.getId())
                                .collectList()
                                .map(caracs -> {
                                    Plan plan = toDomain(planEntity);
                                    plan.setCaracteristicas(caracs);
                                    return plan;
                                })
                );
    }

    @Override
    public Mono<Plan> save(Plan plan) {
        PlanEntity entity = toEntity(plan);
        entity.setActivo(true);
        return planR2dbcRepository.save(entity)
                .map(this::toDomain);
    }

    @Override
    public Mono<Plan> update(Plan plan) {
        return planR2dbcRepository.findById(plan.getId())
                .flatMap(existing -> {
                    existing.setNombre(plan.getNombre());
                    existing.setDescripcion(plan.getDescripcion());
                    existing.setPrecioMensual(plan.getPrecioMensual());
                    existing.setActivo(plan.getActivo());
                    // REQ-SAAS-001 — Sub-fase 1.2: propagar los nuevos campos del esquema Freemium.
                    existing.setCodigo(plan.getCodigo());
                    existing.setDuracionDias(plan.getDuracionDias());
                    existing.setEsGratuito(plan.isEsGratuito());
                    existing.setPlanDegradacionId(plan.getPlanDegradacionId());
                    existing.setMaxSucursales(plan.getMaxSucursales());
                    existing.setMaxClientesActivos(plan.getMaxClientesActivos());
                    existing.setMaxStaff(plan.getMaxStaff());
                    existing.setMoneda(plan.getMoneda());
                    existing.setEsLegacy(plan.isEsLegacy());
                    return planR2dbcRepository.save(existing);
                })
                .map(this::toDomain);
    }

    @Override
    public Mono<Void> deleteCaracteristicasByPlanId(Long planId) {
        return databaseClient.sql("DELETE FROM saas.plan_caracteristicas WHERE id_plan = :idPlan")
                .bind("idPlan", planId)
                .then();
    }

    @Override
    public Mono<Void> saveCaracteristicaRelations(Long planId, List<Long> caracteristicaIds) {
        if (caracteristicaIds == null || caracteristicaIds.isEmpty()) {
            return Mono.empty();
        }
        return Flux.fromIterable(caracteristicaIds)
                .flatMap(caracteristicaId ->
                        databaseClient.sql("INSERT INTO saas.plan_caracteristicas (id_plan, id_caracteristica) VALUES (:idPlan, :idCaracteristica)")
                                .bind("idPlan", planId)
                                .bind("idCaracteristica", caracteristicaId)
                                .then()
                )
                .then();
    }

    @Override
    public Flux<Plan> findAllWithCaracteristicas() {
        return planR2dbcRepository.findAll()
                .flatMap(planEntity ->
                        loadCaracteristicas(planEntity.getId())
                                .collectList()
                                .map(caracs -> {
                                    Plan plan = toDomain(planEntity);
                                    plan.setCaracteristicas(caracs);
                                    return plan;
                                })
                );
    }

    @Override
    public Mono<Plan> findByIdWithCaracteristicas(Long id) {
        return planR2dbcRepository.findById(id)
                .flatMap(planEntity ->
                        loadCaracteristicas(planEntity.getId())
                                .collectList()
                                .map(caracs -> {
                                    Plan plan = toDomain(planEntity);
                                    plan.setCaracteristicas(caracs);
                                    return plan;
                                })
                );
    }

    private Flux<Caracteristica> loadCaracteristicas(Long planId) {
        return databaseClient.sql(
                "SELECT c.* FROM saas.caracteristicas c " +
                "JOIN saas.plan_caracteristicas pc ON c.id = pc.id_caracteristica " +
                "WHERE pc.id_plan = :planId")
                .bind("planId", planId)
                .map((row, metadata) -> {
                    Caracteristica c = new Caracteristica();
                    c.setId(row.get("id", Long.class));
                    c.setCodigo(row.get("codigo", String.class));
                    c.setNombre(row.get("nombre", String.class));
                    c.setModulo(row.get("modulo", String.class));
                    c.setActivo(row.get("activo", Boolean.class));
                    return c;
                })
                .all();
    }

    private Plan toDomain(PlanEntity entity) {
        Plan plan = new Plan();
        plan.setId(entity.getId());
        plan.setNombre(entity.getNombre());
        plan.setDescripcion(entity.getDescripcion());
        plan.setPrecioMensual(entity.getPrecioMensual());
        plan.setActivo(entity.getActivo());
        plan.setCaracteristicas(new ArrayList<>());

        // REQ-SAAS-001 — Sub-fase 1.2: nuevos campos del esquema Free / Trial / Premium.
        plan.setCodigo(entity.getCodigo());
        plan.setDuracionDias(entity.getDuracionDias());
        plan.setEsGratuito(Boolean.TRUE.equals(entity.getEsGratuito()));
        plan.setPlanDegradacionId(entity.getPlanDegradacionId());
        plan.setMaxSucursales(entity.getMaxSucursales());
        plan.setMaxClientesActivos(entity.getMaxClientesActivos());
        plan.setMaxStaff(entity.getMaxStaff());
        plan.setMoneda(entity.getMoneda());
        plan.setEsLegacy(Boolean.TRUE.equals(entity.getEsLegacy()));

        return plan;
    }

    private PlanEntity toEntity(Plan plan) {
        PlanEntity entity = new PlanEntity();
        entity.setId(plan.getId());
        entity.setNombre(plan.getNombre());
        entity.setDescripcion(plan.getDescripcion());
        entity.setPrecioMensual(plan.getPrecioMensual());
        entity.setActivo(plan.getActivo());

        entity.setCodigo(plan.getCodigo());
        entity.setDuracionDias(plan.getDuracionDias());
        entity.setEsGratuito(plan.isEsGratuito());
        entity.setPlanDegradacionId(plan.getPlanDegradacionId());
        entity.setMaxSucursales(plan.getMaxSucursales());
        entity.setMaxClientesActivos(plan.getMaxClientesActivos());
        entity.setMaxStaff(plan.getMaxStaff());
        entity.setMoneda(plan.getMoneda());
        entity.setEsLegacy(plan.isEsLegacy());

        return entity;
    }
}
