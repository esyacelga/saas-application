package com.gymadmin.platform.domain.port.in;

import com.gymadmin.platform.domain.model.CompaniaPlan;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

public interface SuscripcionUseCase {

    Mono<CompaniaPlan> getSuscripcionActiva(Long idCompania);

    Flux<CompaniaPlan> getHistorial(Long idCompania);

    Mono<CompaniaPlan> renovar(Long idCompania, RenovarCommand command);

    Mono<UpgradeResult> upgrade(Long idCompania, UpgradeCommand command);

    Mono<DowngradeResult> downgrade(Long idCompania, DowngradeCommand command);

    record RenovarCommand(
            Long idPlan,
            Integer meses
    ) {}

    record UpgradeCommand(
            Long idPlanNuevo
    ) {}

    record DowngradeCommand(
            Long idPlanNuevo
    ) {}

    record UpgradeResult(
            Long idCompaniaPlanNuevo,
            BigDecimal creditoAplicado,
            BigDecimal montoAPagar,
            Boolean planAnteriorCancelado
    ) {}

    record DowngradeResult(
            Long idCompaniaPlanNuevo,
            String estado,
            java.time.LocalDate efectivoDe,
            BigDecimal creditoGenerado
    ) {}
}
