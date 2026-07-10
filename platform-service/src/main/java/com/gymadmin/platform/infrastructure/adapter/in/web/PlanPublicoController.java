package com.gymadmin.platform.infrastructure.adapter.in.web;

import com.gymadmin.platform.domain.port.out.PlanRepository;
import com.gymadmin.platform.infrastructure.adapter.in.web.dto.PlanPublicoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * REQ-SAAS-001 (Sub-fase 1.4): endpoint público (sin auth) del catálogo de planes
 * publicables — Free, Trial y Premium — para landing y wizard de auto-registro.
 * <p>
 * A diferencia de {@link PlanController#listarPlanesPublicos()}, este endpoint
 * entrega el shape completo del plan Freemium (código, duración, es_gratuito,
 * cuotas duras, moneda) mediante {@link PlanPublicoResponse}.
 */
@RestController
@RequestMapping("/api/v1/planes")
@Tag(name = "Planes públicos", description = "Catálogo público de planes SaaS Freemium")
public class PlanPublicoController {

    private final PlanRepository planRepository;

    public PlanPublicoController(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Operation(summary = "Catálogo público de planes activos no legacy (Free, Trial, Premium)")
    @ApiResponse(responseCode = "200", description = "Lista de planes publicables")
    @GetMapping("/publicos")
    public Flux<PlanPublicoResponse> listarPublicos() {
        return planRepository.findByActivoTrueAndEsLegacyFalse()
                .map(PlanPublicoResponse::from);
    }
}
